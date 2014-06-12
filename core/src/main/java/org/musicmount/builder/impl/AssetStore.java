/*
 * Copyright 2013-2014 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.musicmount.builder.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Track;
import org.musicmount.io.Resource;
import org.musicmount.util.ProgressHandler;

import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.json.JsonXMLStreamConstants;
import de.odysseus.staxon.json.JsonXMLStreamWriter;

public class AssetStore {
	static final Logger LOGGER = Logger.getLogger(AssetStore.class.getName());

	static class AssetEntity {
		enum State {
			Synced,
			Created,
			Modified
		}

		final Asset asset;

		State state;
		Long albumId;

		AssetEntity(Long albumId, Asset asset, State state) {
			this.albumId = albumId;
			this.asset = asset;
			this.state = state;
		}
	}
	
	final Map<Resource, AssetEntity> entities = new LinkedHashMap<Resource, AssetEntity>();
	final Set<Long> deletedAlbumIds = new HashSet<Long>();
	final Resource musicFolder;

	final String version; // store format version
	
	long timestamp = System.currentTimeMillis();
	Boolean retina = null; // null means "unknown"

	public AssetStore(String apiVersion, Resource musicFolder) {
		this.version = apiVersion + "-2";
		this.musicFolder = musicFolder;
	}
	
	/*
	 * visible for testing
	 */
	public Asset getAsset(Resource resource) {
		return entities.containsKey(resource) ? entities.get(resource).asset : null;
	}
	
	/*
	 * visible for testing
	 */
	Set<Long> getDeletedAlbumIds() {
		return Collections.unmodifiableSet(deletedAlbumIds);
	}
	
	public Boolean getRetina() {
		return retina;
	}
	
	public void setRetina(Boolean retina) {
		this.retina = retina;
	}
	
	public Map<Resource, AssetEntity> getEntities() {
		return Collections.unmodifiableMap(entities);
	}
	
	public Iterable<Asset> assets() {
		return new Iterable<Asset>() {
			Iterator<AssetEntity> delegate = entities.values().iterator();
			@Override
			public Iterator<Asset> iterator() {
				return new Iterator<Asset>() {
					@Override
					public boolean hasNext() {
						return delegate.hasNext();
					}
					@Override
					public Asset next() {
						return delegate.next().asset;
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	/**
	 * Synchronize store with albums.
	 * This assigns album ids to the albums.
	 * The store's entities are updated to reflect the assigned album ids.
	 * 
	 * Answer set of changed albums since last sync, i.e.:
	 * <ul>
	 * <li>the album has tracks that are modified lately</li>
	 * <li>tracks have been removed from the album</li>
	 * <li>tracks have been added to the album</li>
	 * </ul>
	 * @param albums albums to sync
	 * @return changed albums
	 */
	public Set<Album> sync(Iterable<Album> albums) {
		Set<Album> changedAlbums = new HashSet<Album>();

		/*
		 * reserve entity album id candidates
		 */
		Set<Long> reservedAlbumIds = new HashSet<Long>();
		for (AssetEntity entity : entities.values()) {
			reservedAlbumIds.add(entity.albumId);
		}
		
		/*
		 * album ids from reserved entities that have been assigned
		 */
		Set<Long> bookedAlbumIds = new HashSet<Long>();
		
		/*
		 * entities with corresponding album tracks in library
		 */
		Set<AssetEntity> coveredEntities = new HashSet<AssetEntity>();

		long albumIdSequence = 0; // album id sequence to find next unreserved album id

		for (Album album : albums) {
			/*
			 * (1) find first available album id from a covered entity
			 */
			Long albumId = null;
			for (Track track : album.getTracks()) {
				AssetEntity entity = entities.get(track.getResource());
				if (entity != null && entity.albumId != null && !bookedAlbumIds.contains(entity.albumId)) {
					albumId = entity.albumId;
					bookedAlbumIds.add(albumId);
					break;
				}
			}

			/*
			 * (2) could not find album id in (1) -> grab a new (unreserved) id
			 */
			if (albumId == null) {
				while (reservedAlbumIds.contains(albumIdSequence)) {
					++albumIdSequence;
				}
				albumId = albumIdSequence++;
			}
			
			/*
			 * (3) assign album id to album and mark id as assigned (used)
			 */
			album.setAlbumId(albumId);

			/*
			 * (4) determine album change, update album id of entities covered by the album, collect covered entities
			 */
			boolean albumChanged = deletedAlbumIds.contains(albumId);
			for (Track track : album.getTracks()) {
				AssetEntity entity = entities.get(track.getResource());
				if (entity != null) {
					if (entity.albumId == null || entity.albumId.longValue() != albumId.longValue() || entity.state != AssetEntity.State.Synced) {
						albumChanged = true;
					}
					entity.albumId = albumId;
					coveredEntities.add(entity);
				}
			}
			if (albumChanged) {
				changedAlbums.add(album);
			}
		}

		/*
		 * reset deleted album ids
		 */
		deletedAlbumIds.clear();
		
		/*
		 * clear album id for uncovered entities, mark all entities as synchronized
		 */
		for (AssetEntity entity : entities.values()) {
			if (!coveredEntities.contains(entity)) {
				entity.albumId = null;
			}
			entity.state = AssetEntity.State.Synced;
		}
		
		return changedAlbums;
	}

	void writeNumberProperty(JsonXMLStreamWriter writer, String name, Number value) throws XMLStreamException {
		if (value != null) {
			writer.writeStartElement(name);
			writer.writeNumber(value);
			writer.writeEndElement();
		}
	}

	void writeStringProperty(JsonXMLStreamWriter writer, String name, String value) throws XMLStreamException {
		if (value != null) {
			writer.writeStartElement(name);
			writer.writeCharacters(value);
			writer.writeEndElement();
		}
	}

	void writeBooleanProperty(JsonXMLStreamWriter writer, String name, Boolean value) throws XMLStreamException {
		if (value != null) {
			writer.writeStartElement(name);
			writer.writeBoolean(value);
			writer.writeEndElement();
		}
	}

	public void save(OutputStream output) throws IOException, XMLStreamException {
		JsonXMLOutputFactory factory = new JsonXMLOutputFactory(new JsonXMLConfigBuilder().prettyPrint(false).virtualRoot("assetStore").build());
		JsonXMLStreamWriter writer = factory.createXMLStreamWriter(output);
		try {
			writer.writeStartDocument();
			writer.writeStartElement("assetStore");
			writeStringProperty(writer, "version", version);
			writeNumberProperty(writer, "timestamp", timestamp);
			if (retina != null) {
				writeBooleanProperty(writer, "retina", retina);
			}
			writer.writeProcessingInstruction(JsonXMLStreamConstants.MULTIPLE_PI_TARGET);
			for (AssetEntity entity : entities.values()) {
				String assetPath;
				try {
					assetPath = musicFolder.getPath().relativize(entity.asset.getResource().getPath()).toString();
				} catch (IllegalArgumentException e) {
					LOGGER.warning("Could not determine path for asset resource: " + entity.asset.getResource());
					continue;
				}
				writer.writeStartElement("asset");
				writeNumberProperty(writer, "albumId", entity.albumId);
				writeStringProperty(writer, "album", entity.asset.getAlbum());
				writeStringProperty(writer, "albumArtist", entity.asset.getAlbumArtist());
				writeStringProperty(writer, "artist", entity.asset.getArtist());
				writeBooleanProperty(writer, "artworkAvailable", entity.asset.isArtworkAvailable());
				writeStringProperty(writer, "assetPath", assetPath);
				writeBooleanProperty(writer, "compilation", entity.asset.isCompilation());
				writeStringProperty(writer, "composer", entity.asset.getComposer());
				writeNumberProperty(writer, "discNumber", entity.asset.getDiscNumber());
				writeNumberProperty(writer, "duration", entity.asset.getDuration());
				writeStringProperty(writer, "genre", entity.asset.getGenre());
				writeStringProperty(writer, "grouping", entity.asset.getGrouping());
				writeStringProperty(writer, "name", entity.asset.getName());
				writeNumberProperty(writer, "trackNumber", entity.asset.getTrackNumber());
				writeNumberProperty(writer, "year", entity.asset.getYear());
				writer.writeEndElement();
			}
			writer.writeEndElement();
			writer.writeEndDocument();
		} finally {
			writer.close();
		}
	}

	public void save(Resource assetStoreFile, ProgressHandler progressHandler) throws IOException, XMLStreamException {
		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Saving asset store...");
		}
		OutputStream output = assetStoreFile.getOutputStream();
		if (assetStoreFile.getName().endsWith(".gz")) {
			output = new GZIPOutputStream(output);
		}
		try (OutputStream assetStoreOutput = new BufferedOutputStream(output)) {
			save(assetStoreOutput);
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}
	}

	synchronized void updateEntity(Resource resource, AssetParser assetParser) throws Exception {
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Parsing asset: " + resource.getPath());
		}
		AssetEntity entity = entities.remove(resource);
		Asset asset = assetParser.parse(resource);
		if (entity == null) { // new asset
			entities.put(resource, entity = new AssetEntity(null, asset, AssetEntity.State.Created));
		} else { // modified asset -> keep albumId
			entities.put(resource, entity = new AssetEntity(entity.albumId, asset, AssetEntity.State.Modified));
		}
	}
	
	void updateEntities(final AssetParser assetParser, Set<Resource> assetResources, int maxThreads, final ProgressHandler progressHandler) throws IOException {
		final List<Resource> trashList = new ArrayList<>(); // deleted/bad resources

		/*
		 * collect deleted resources
		 */
		for (Resource resource : entities.keySet()) {
			if (!assetResources.contains(resource)) {
				trashList.add(resource);
			}
		}

		/*
		 * prepare parse list
		 */
		List<Resource> parseList = new ArrayList<>(); // new/modified resources
		if (entities.isEmpty()) {
			parseList.addAll(assetResources);
		} else {
			if (progressHandler != null) {
				progressHandler.beginTask(assetResources.size(), "Collecting new/modified assets...");
			}
			final int progressModulo = 2500;
			int progressCount = 0;
			for (Resource resource : assetResources) {
				if (!entities.containsKey(resource) || resource.lastModified() > timestamp) { // new/modified asset -> parse
					parseList.add(resource);
				}
				progressCount++;
				if (progressHandler != null && progressCount % progressModulo == 0) {
					progressHandler.progress(progressCount, String.format("#assets = %5d", progressCount));
				}
			}
			if (progressHandler != null) {
				progressHandler.endTask();
			}
		}

		/*
		 * Parse new/modified entities
		 */
		if (!parseList.isEmpty()) {
			int numberOfAssetsPerTask = 10;
			int numberOfAssets = parseList.size();
			int numberOfThreads = Math.min(1 + (numberOfAssets - 1) / numberOfAssetsPerTask, Math.min(maxThreads, Runtime.getRuntime().availableProcessors()));
			if (progressHandler != null) {
				progressHandler.beginTask(parseList.size(), String.format("Parsing %d assets...", numberOfAssets));
			}
			final int progressModulo = numberOfAssets < 200 ? 10 : numberOfAssets < 1000 ? 50 : 100;
			if (numberOfThreads > 1) { // run on multiple threads
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("Parallel: #threads = " + numberOfThreads);
				}
				ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
				final AtomicInteger atomicProgressCount = new AtomicInteger();
				for (int start = 0; start < numberOfAssets; start += numberOfAssetsPerTask) {
					final Collection<Resource> assetsSlice = parseList.subList(start, Math.min(numberOfAssets, start + numberOfAssetsPerTask));
					executor.execute(new Runnable() {
						@Override
						public void run() {
							for (Resource resource : assetsSlice) {
								try {
									updateEntity(resource, assetParser);
								} catch (Exception e) {
									trashList.add(resource);
									LOGGER.log(Level.WARNING, "Could not parse asset: " + resource.getPath(), e);
								}
								int count = atomicProgressCount.getAndIncrement() + 1;
								if (progressHandler != null && count % progressModulo == 0) {
									progressHandler.progress(count, String.format("#assets = %5d", count));
								}
							}
						}
					});
				}
				executor.shutdown();
				try {
					executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					LOGGER.warning("Interrupted: " + e.getMessage());
				}
			} else { // run in current thread
				int progressCount = 0;
				for (Resource resource : parseList) {
					try {
						updateEntity(resource, assetParser);
					} catch (Exception e) {
						trashList.add(resource);
						LOGGER.log(Level.WARNING, "Could not parse asset: " + resource.getPath(), e);
					}
					progressCount++;
					if (progressHandler != null && progressCount % progressModulo == 0) {
						progressHandler.progress(progressCount, String.format("#assets = %5d", progressCount));
					}
				}
			}
			if (progressHandler != null) {
				progressHandler.endTask();
			}
		}

		/*
		 * remove entities for deleted/bad resources
		 */
		for (Resource resource : trashList) {
			AssetEntity entity = entities.remove(resource);
			if (entity != null && entity.albumId != null) {
				deletedAlbumIds.add(entity.albumId);
			}
		}
	}

	private void collectAssetResources(Set<Resource> assetResources, final Resource directory, ProgressHandler progressHandler, DirectoryStream.Filter<Path> assetFilter) throws IOException {
		try (DirectoryStream<Resource> directoryStream = directory.newResourceDirectoryStream(assetFilter)) {
			for (Resource resource : directoryStream) {
				if (resource.isDirectory()) {
					collectAssetResources(assetResources, resource, progressHandler, assetFilter);
				} else {
					assetResources.add(resource);
					if (progressHandler != null && assetResources.size() % 1000 == 0) {
						progressHandler.progress(assetResources.size(), String.format("#assets = %5d", assetResources.size()));
					}
				}
			}
		}
	}

	Set<Resource> collectAssetResources(ProgressHandler progressHandler, DirectoryStream.Filter<Path> assetFilter) throws IOException {
		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Scanning directory for assets...");
		}
		Set<Resource> assetResources = new LinkedHashSet<>();
		collectAssetResources(assetResources, musicFolder, progressHandler, assetFilter);
		if (progressHandler != null) {
			progressHandler.endTask();
		}
		return assetResources;
	}

	public void update(final AssetParser assetParser, int maxThreads, ProgressHandler progressHandler) throws IOException {
		long updateTimestamp = System.currentTimeMillis();

		/*
		 * collect resources
		 */
		Set<Resource> assetResources = collectAssetResources(progressHandler, new DirectoryStream.Filter<Path>() {
			public boolean accept(Path path) {
				try {
					return !path.getFileName().toString().startsWith(".") && (assetParser.isAssetPath(path) || musicFolder.getProvider().isDirectory(path));
				} catch (IOException e) {
					return false;
				}
			}
		});

		/*
		 * parse assets
		 */
		updateEntities(assetParser, assetResources, maxThreads, progressHandler);

		/*
		 * update timestamp
		 */
		timestamp = updateTimestamp;
	}

	private AssetEntity loadEntity(XMLStreamReader reader) throws IOException, XMLStreamException {
		reader.require(XMLStreamConstants.START_ELEMENT, null, "asset");
		reader.nextTag();

		Long albumId = null;
		String album = null;
		String albumArtist = null;
		String artist = null;
		boolean artworkAvailable = false;
		String assetPath = null;
		boolean compilation = false;
		String composer = null;
		Integer discNumber = null;
		Integer duration = null;
		String genre = null;
		String grouping = null;
		String name = null;
		Integer trackNumber = null;
		Integer year = null;
		while (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
			switch (reader.getLocalName()) {
			case "albumId":
				albumId = Long.valueOf(reader.getElementText());
				break;
			case "album":
				album = reader.getElementText();
				break;
			case "albumArtist":
				albumArtist = reader.getElementText();
				break;
			case "artist":
				artist = reader.getElementText();
				break;
			case "artworkAvailable":
				artworkAvailable = Boolean.valueOf(reader.getElementText());
				break;
			case "assetPath":
				assetPath = reader.getElementText();
				break;
			case "compilation":
				compilation = Boolean.valueOf(reader.getElementText());
				break;
			case "composer":
				composer = reader.getElementText();
				break;
			case "discNumber":
				discNumber = Integer.valueOf(reader.getElementText());
				break;
			case "duration":
				duration = Integer.valueOf(reader.getElementText());
				break;
			case "genre":
				genre = reader.getElementText();
				break;
			case "grouping":
				grouping = reader.getElementText();
				break;
			case "name":
				name = reader.getElementText();
				break;
			case "trackNumber":
				trackNumber = Integer.valueOf(reader.getElementText());
				break;
			case "year":
				year = Integer.valueOf(reader.getElementText());
				break;
			default:
				throw new XMLStreamException("unexpected asset property: " + reader.getLocalName());
			}
			reader.require(XMLStreamConstants.END_ELEMENT, null, null);
			reader.nextTag();
		}
		reader.require(XMLStreamConstants.END_ELEMENT, null, "asset");
		
		if (assetPath == null) {
			throw new XMLStreamException("Missing 'assetPath'");
		}
		
		Asset asset = null;
		try {
			asset = new Asset(musicFolder.resolve(assetPath));
		} catch (InvalidPathException e) {
			LOGGER.warning("Could not locate asset resource for path: " + assetPath);
			return null;
		}
		asset.setAlbum(album);
		asset.setAlbumArtist(albumArtist);
		asset.setArtist(artist);
		asset.setArtworkAvailable(artworkAvailable);
		asset.setCompilation(compilation);
		asset.setComposer(composer);
		asset.setDiscNumber(discNumber);
		asset.setDuration(duration);
		asset.setGenre(genre);
		asset.setGrouping(grouping);
		asset.setName(name);
		asset.setTrackNumber(trackNumber);
		asset.setYear(year);

		return new AssetEntity(albumId, asset, AssetEntity.State.Synced);
	}

	public void load(InputStream input) throws IOException, XMLStreamException {
		XMLInputFactory factory = new JsonXMLInputFactory(new JsonXMLConfigBuilder().virtualRoot("assetStore").build());
		XMLStreamReader reader = factory.createXMLStreamReader(input);
		try {
			if (reader.getEventType() == XMLStreamConstants.START_DOCUMENT) {
				reader.nextTag();
			}
			reader.require(XMLStreamConstants.START_ELEMENT, null, "assetStore");
			reader.nextTag();
			while (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
				switch (reader.getLocalName()) {
				case "version":
					String version = reader.getElementText();
					if (!this.version.equals(version)) {
						throw new IOException("incompatible store version");
					}
					break;
				case "timestamp":
					timestamp = Long.valueOf(reader.getElementText());
					break;
				case "asset":
					AssetEntity entity = loadEntity(reader);
					if (entity != null) {
						entities.put(entity.asset.getResource(), entity);
						if (LOGGER.isLoggable(Level.FINEST)) {
							LOGGER.finest("Asset has been loaded: " + entity.asset.getResource().getPath().toAbsolutePath());
						}
					}
					break;
				case "retina":
					retina = Boolean.valueOf(reader.getElementText());
					break;
				default:
					throw new XMLStreamException("unexpected store property: " + reader.getLocalName());
				}
				reader.require(XMLStreamConstants.END_ELEMENT, null, null);
				reader.nextTag();
			}
			reader.require(XMLStreamConstants.END_ELEMENT, null, "assetStore");
		} finally {
			reader.close();
		}
	}
	
	public void load(Resource assetStoreFile, ProgressHandler progressHandler) throws IOException, XMLStreamException {
		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Loading asset store...");
		}
		InputStream input = assetStoreFile.getInputStream();
		if (assetStoreFile.getName().endsWith(".gz")) {
			input = new GZIPInputStream(input);
		}
		try (InputStream assetStoreInput = new BufferedInputStream(input)) {
			load(assetStoreInput);
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}
	}
}
