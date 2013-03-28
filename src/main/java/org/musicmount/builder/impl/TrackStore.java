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
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Track;

import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.json.JsonXMLStreamConstants;
import de.odysseus.staxon.json.JsonXMLStreamWriter;

public class TrackStore {
	static final Logger LOGGER = Logger.getLogger(TrackStore.class.getName());

	static class TrackEntity {
		final long albumId;
		final Track track;
		
		public TrackEntity(long albumId, Track track) {
			this.albumId = albumId;
			this.track = track;
		}
	}
	
	final Map<File, TrackEntity> entities = new LinkedHashMap<File, TrackStore.TrackEntity>();
	final Set<Long> loadedAlbumIds = new HashSet<Long>();
	final Set<Long> changedAlbumIds = new HashSet<Long>();
	final Set<Long> createdAlbumIds = new HashSet<Long>();
	final String apiVersion;
	
	long albumIdSequence = 0;
	long timestamp = System.currentTimeMillis();

	public TrackStore(String apiVersion) {
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
	
	public Map<File, TrackEntity> getEntities() {
		return Collections.unmodifiableMap(entities);
	}

	long nextAlbumId() {
		while (loadedAlbumIds.contains(albumIdSequence) || createdAlbumIds.contains(albumIdSequence)) {
			albumIdSequence++;
		}
		return albumIdSequence;
	}
	
	/**
	 * Add first track for a new album
	 * @param track
	 * @param title
	 * @return album
	 */
	public Album createAlbum(Track track, String title) {
		long albumId;
		/*
		 * if the track was loaded from the store, check if we
		 * already created an album from the track's original album id.
		 */
		if (entities.containsKey(track.getAssetFile())) {
			long oldAlbumId = entities.get(track.getAssetFile()).albumId;
			if (createdAlbumIds.contains(oldAlbumId)) { // album split
				changedAlbumIds.add(oldAlbumId);
				albumId = nextAlbumId();
				changedAlbumIds.add(albumId);
			} else {
				albumId = oldAlbumId;
			}
		} else { // never seen this track before -> create new album id
			albumId = nextAlbumId();
			changedAlbumIds.add(albumId);
		}
		entities.put(track.getAssetFile(), new TrackEntity(albumId, track));
		Album album = new Album(albumId, title);
		createdAlbumIds.add(albumId);
		return album;
	}

	/**
	 * Add track to previously created album
	 * @param track
	 * @param album
	 */
	public void addTrack(Track track, Album album) {
		long albumId = album.getAlbumId();
		/*
		 * album must be created via createAlbum(...)
		 */
		if (!createdAlbumIds.contains(albumId)) {
			throw new IllegalArgumentException("Invalid album id: " + albumId);
		}
		/*
		 * check if track is moving to a new album
		 */
		if (entities.containsKey(track.getAssetFile())) {
			long oldAlbumId = entities.get(track.getAssetFile()).albumId;
			if (oldAlbumId != albumId) {
				changedAlbumIds.add(albumId);
				changedAlbumIds.add(oldAlbumId);
			}
		} else { // track not seen before
			changedAlbumIds.add(albumId);
		}
		entities.put(track.getAssetFile(), new TrackEntity(albumId, track));
	}

	public Track getTrack(File assetFile) {
		return entities.containsKey(assetFile) ? entities.get(assetFile).track : null;
	}
	
	/**
	 * Answer <code>true</code> if the specified album has changed somehow, i.e.:
	 * <ul>
	 * <li>the album has tracks that are modified lately</li>
	 * <li>tracks have been removed from the album</li>
	 * <li>tracks have been added to the album</li>
	 * </ul>
	 * @param album
	 * @return <code>true</code> if the specified album has changed
	 */
	public boolean isAlbumChanged(Album album) {
		return changedAlbumIds.contains(album.getAlbumId());
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
		JsonXMLOutputFactory factory = new JsonXMLOutputFactory(new JsonXMLConfigBuilder().prettyPrint(false).virtualRoot("trackStore").build());
		JsonXMLStreamWriter writer = factory.createXMLStreamWriter(output);
		try {
			writer.writeStartDocument();
			writer.writeStartElement("trackStore");
			writeStringProperty(writer, "apiVersion", apiVersion);
			writeNumberProperty(writer, "timestamp", timestamp);
			writer.writeProcessingInstruction(JsonXMLStreamConstants.MULTIPLE_PI_TARGET);
			for (TrackEntity entity : entities.values()) {
				String assetPath = assetLocator.getAssetPath(entity.track.getAssetFile());
				if (assetPath != null) {
					writer.writeStartElement("track");
					writeNumberProperty(writer, "albumId", entity.albumId);
					writeStringProperty(writer, "album", entity.track.getAlbum());
					writeStringProperty(writer, "albumArtist", entity.track.getAlbumArtist());
					writeStringProperty(writer, "artist", entity.track.getArtist());
					writeBooleanProperty(writer, "artworkAvailable", entity.track.isArtworkAvailable());
					writeStringProperty(writer, "assetPath", assetPath);
					writeBooleanProperty(writer, "compilation", entity.track.isCompilation());
					writeStringProperty(writer, "composer", entity.track.getComposer());
					writeNumberProperty(writer, "discNumber", entity.track.getDiscNumber());
					writeNumberProperty(writer, "duration", entity.track.getDuration());
					writeStringProperty(writer, "genre", entity.track.getGenre());
					writeStringProperty(writer, "name", entity.track.getName());
					writeNumberProperty(writer, "trackNumber", entity.track.getTrackNumber());
					writeNumberProperty(writer, "year", entity.track.getYear());
					writer.writeEndElement();
				}
			}
			writer.writeEndElement();
			writer.writeEndDocument();
		} finally {
			writer.close();
		}
	}

	private TrackEntity readTrack(XMLStreamReader reader, AssetLocator assetLocator) throws XMLStreamException {
		reader.require(XMLStreamConstants.START_ELEMENT, null, "track");
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
				throw new XMLStreamException("unexpected track property: " + reader.getLocalName());
			}
			reader.require(XMLStreamConstants.END_ELEMENT, null, null);
			reader.nextTag();
		}
		reader.require(XMLStreamConstants.END_ELEMENT, null, "track");

		if (albumId != null && assetPath != null) {
			File assetFile = assetLocator.getAssetFile(assetPath);
			if (assetFile != null && assetFile.exists() && assetFile.lastModified() < timestamp) {
				Track track = new Track(assetFile);
				track.setAlbum(album);
				track.setAlbumArtist(albumArtist);
				track.setArtist(artist);
				track.setArtworkAvailable(artworkAvailable);
				track.setCompilation(compilation);
				track.setComposer(composer);
				track.setDiscNumber(discNumber);
				track.setDuration(duration);
				track.setGenre(genre);
				track.setName(name);
				track.setTrackNumber(trackNumber);
				track.setYear(year);
				return new TrackEntity(albumId, track);
			}
		}
		return null;
	}
	
	public void load(InputStream input, AssetLocator assetLocator) throws IOException, XMLStreamException {
		JsonXMLInputFactory factory = new JsonXMLInputFactory(new JsonXMLConfigBuilder().virtualRoot("trackStore").build());
		XMLStreamReader reader = factory.createXMLStreamReader(input);
		try {
			if (reader.getEventType() == XMLStreamConstants.START_DOCUMENT) {
				reader.nextTag();
			}
			reader.require(XMLStreamConstants.START_ELEMENT, null, "trackStore");
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
					timestamp = Math.min(timestamp, Long.valueOf(reader.getElementText()));
					break;
				case "track":
					TrackEntity entity = readTrack(reader, assetLocator);
					if (entity != null) {
						entities.put(entity.track.getAssetFile(), entity);
						loadedAlbumIds.add(entity.albumId);
					}
					break;
				default:
					throw new XMLStreamException("unexpected trackStore property: " + reader.getLocalName());
				}
				reader.require(XMLStreamConstants.END_ELEMENT, null, null);
				reader.nextTag();
			}
			reader.require(XMLStreamConstants.END_ELEMENT, null, "trackStore");
		} finally {
			reader.close();
		}
		
	}
}
