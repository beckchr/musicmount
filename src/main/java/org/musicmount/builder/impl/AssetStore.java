/*
 * Copyright 2013 Odysseus Software GmbH
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.json.JsonXMLStreamConstants;
import de.odysseus.staxon.json.JsonXMLStreamWriter;

public class AssetStore {
	static final Logger LOGGER = Logger.getLogger(AssetStore.class.getName());

	static class AssetEntity {
		final long albumId;
		final File file;
		final Asset asset;
		
		AssetEntity(long albumId, Asset asset) {
			this.albumId = albumId;
			this.file = asset.getFile();
			this.asset = asset;
		}

		AssetEntity(long albumId, File file) {
			this.albumId = albumId;
			this.file = file;
			this.asset = null;
		}
	}
	
	final Map<File, AssetEntity> entities = new LinkedHashMap<File, AssetStore.AssetEntity>();
	final Set<Long> loadedAlbumIds = new HashSet<Long>();
	final Set<Long> changedAlbumIds = new HashSet<Long>();
	final Set<Long> createdAlbumIds = new HashSet<Long>();
	final String apiVersion;
	
	long albumIdSequence = 0;
	long timestamp = System.currentTimeMillis();

	public AssetStore(String apiVersion) {
		this.apiVersion = apiVersion;
	}
	
	/*
	 * visible for testing
	 */
	Set<Long> getLoadedAlbumIds() {
		return Collections.unmodifiableSet(loadedAlbumIds);
	}
	
	/*
	 * visible for testing
	 */
	Set<Long> getCreatedAlbumIds() {
		return Collections.unmodifiableSet(createdAlbumIds);
	}
	
	/*
	 * visible for testing
	 */
	Set<Long> getChangedAlbumIds() {
		return Collections.unmodifiableSet(changedAlbumIds);
	}
	
	public Map<File, AssetEntity> getEntities() {
		return Collections.unmodifiableMap(entities);
	}

	long nextAlbumId() {
		while (loadedAlbumIds.contains(albumIdSequence) || createdAlbumIds.contains(albumIdSequence)) {
			albumIdSequence++;
		}
		return albumIdSequence;
	}
	
	/**
	 * Add first asset for a new album
	 * @param asset
	 * @param title
	 * @return album id
	 */
	public long createAlbum(Asset asset) {
		long albumId;
		/*
		 * if the asset was loaded from the store, check if we
		 * already created an album from the asset's original album id.
		 */
		if (entities.containsKey(asset.getFile())) {
			long oldAlbumId = entities.get(asset.getFile()).albumId;
			if (createdAlbumIds.contains(oldAlbumId)) { // album split
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("Album split albumId for assset: " + asset.getFile());
				}
				changedAlbumIds.add(oldAlbumId);
				albumId = nextAlbumId();
				changedAlbumIds.add(albumId);
			} else {
				albumId = oldAlbumId;
			}
		} else { // never seen this asset before -> create new album id
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Creating new albumId for assset: " + asset.getFile());
			}
			albumId = nextAlbumId();
			changedAlbumIds.add(albumId);
		}
		entities.put(asset.getFile(), new AssetEntity(albumId, asset));
		createdAlbumIds.add(albumId);
		return albumId;
	}

	/**
	 * Add asset to previously created album
	 * @param asset
	 * @param album
	 */
	public void addAsset(Asset asset, long albumId) {
		/*
		 * album must be created via createAlbum(...)
		 */
		if (!createdAlbumIds.contains(albumId)) {
			throw new IllegalArgumentException("Invalid album id: " + albumId);
		}
		/*
		 * check if asset is moving to a new album
		 */
		if (entities.containsKey(asset.getFile())) {
			long oldAlbumId = entities.get(asset.getFile()).albumId;
			if (oldAlbumId != albumId) {
				changedAlbumIds.add(albumId);
				changedAlbumIds.add(oldAlbumId);
			}
		} else { // asset not seen before
			changedAlbumIds.add(albumId);
		}
		entities.put(asset.getFile(), new AssetEntity(albumId, asset));
	}

	public Asset getAsset(File assetFile) {
		return entities.containsKey(assetFile) ? entities.get(assetFile).asset : null;
	}
	
	/**
	 * Answer <code>true</code> if the specified album has changed somehow, i.e.:
	 * <ul>
	 * <li>the album has assets that are modified lately</li>
	 * <li>assets have been removed from the album</li>
	 * <li>assets have been added to the album</li>
	 * </ul>
	 * @param album
	 * @return <code>true</code> if the specified album has changed
	 */
	public boolean isAlbumChanged(long albumId) {
		return changedAlbumIds.contains(albumId);
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

	public void save(OutputStream output, AssetLocator assetLocator) throws IOException, XMLStreamException {
		JsonXMLOutputFactory factory = new JsonXMLOutputFactory(new JsonXMLConfigBuilder().prettyPrint(false).virtualRoot("assetStore").build());
		JsonXMLStreamWriter writer = factory.createXMLStreamWriter(output);
		try {
			writer.writeStartDocument();
			writer.writeStartElement("assetStore");
			writeStringProperty(writer, "apiVersion", apiVersion);
			writeNumberProperty(writer, "timestamp", timestamp);
			writer.writeProcessingInstruction(JsonXMLStreamConstants.MULTIPLE_PI_TARGET);
			for (AssetEntity entity : entities.values()) {
				String assetPath = assetLocator.getAssetPath(entity.file);
				if (assetPath != null && entity.asset != null) {
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
					writeStringProperty(writer, "name", entity.asset.getName());
					writeNumberProperty(writer, "trackNumber", entity.asset.getTrackNumber());
					writeNumberProperty(writer, "year", entity.asset.getYear());
					writer.writeEndElement();
				}
			}
			writer.writeEndElement();
			writer.writeEndDocument();
		} finally {
			writer.close();
		}
	}

	private AssetEntity readAsset(XMLStreamReader reader, AssetLocator assetLocator, long timestamp) throws XMLStreamException {
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

		if (albumId != null && assetPath != null) {
			File assetFile = assetLocator.getAssetFile(assetPath);
			if (assetFile != null && assetFile.exists()) {
				if (assetFile.lastModified() < timestamp) {
					Asset asset = new Asset(assetFile);
					asset.setAlbum(album);
					asset.setAlbumArtist(albumArtist);
					asset.setArtist(artist);
					asset.setArtworkAvailable(artworkAvailable);
					asset.setCompilation(compilation);
					asset.setComposer(composer);
					asset.setDiscNumber(discNumber);
					asset.setDuration(duration);
					asset.setGenre(genre);
					asset.setName(name);
					asset.setTrackNumber(trackNumber);
					asset.setYear(year);
					return new AssetEntity(albumId, asset);
				} else {
					LOGGER.finer("Asset has been modified: " + assetFile + " (" + assetFile.lastModified() + ")");
					return new AssetEntity(albumId, assetFile); // cannot reuse asset data, but album id
				}
			} else {
				if (LOGGER.isLoggable(Level.FINER)) {
					if (assetFile == null) {
						LOGGER.finer("Asset unresolved: " + assetPath);
					} else {
						LOGGER.finer("Asset does not exist: " + assetFile);
					}
				}
			}
		}
		return null;
	}
	
	public void load(InputStream input, AssetLocator assetLocator) throws IOException, XMLStreamException {
		long timestamp = 0L;
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
				case "apiVersion":
					String version = reader.getElementText();
					if (!apiVersion.equals(version)) {
						return;
					}
					break;
				case "timestamp":
					timestamp = Long.valueOf(reader.getElementText());
					break;
				case "asset":
					AssetEntity entity = readAsset(reader, assetLocator, timestamp);
					if (entity != null) {
						entities.put(entity.file, entity);
						loadedAlbumIds.add(entity.albumId);
					}
					break;
				default:
					throw new XMLStreamException("unexpected assetStore property: " + reader.getLocalName());
				}
				reader.require(XMLStreamConstants.END_ELEMENT, null, null);
				reader.nextTag();
			}
			reader.require(XMLStreamConstants.END_ELEMENT, null, "assetStore");
		} finally {
			reader.close();
		}
		
	}
}
