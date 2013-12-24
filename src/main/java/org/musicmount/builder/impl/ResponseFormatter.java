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

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Disc;
import org.musicmount.builder.model.Playlist;
import org.musicmount.builder.model.Track;

import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.json.JsonXMLStreamConstants;
import de.odysseus.staxon.json.JsonXMLStreamWriter;
import de.odysseus.staxon.xml.util.PrettyXMLStreamWriter;

public abstract class ResponseFormatter<T extends XMLStreamWriter> {
	public static class JSON extends ResponseFormatter<JsonXMLStreamWriter> {
		private final JsonXMLOutputFactory factory;
		
		public JSON(String apiVersion, LocalStrings localStrings, boolean noDirectoryIndex, boolean includeUnknownGenre, boolean useGrouping, boolean prettyPrint) {
			super(apiVersion, localStrings, noDirectoryIndex ? null : "index.json", includeUnknownGenre, useGrouping);
			factory = new JsonXMLOutputFactory(new JsonXMLConfigBuilder().prettyPrint(prettyPrint).virtualRoot("response").build());
		}

		void writeNumberProperty(JsonXMLStreamWriter writer, String name, Number value) throws XMLStreamException {
			writer.writeStartElement(name);
			writer.writeNumber(value);
			writer.writeEndElement();
		}

		void writeStartArray(JsonXMLStreamWriter writer) throws XMLStreamException {
			writer.writeProcessingInstruction(JsonXMLStreamConstants.MULTIPLE_PI_TARGET);
		}

		JsonXMLStreamWriter createStreamWriter(OutputStream output) throws XMLStreamException {
			return factory.createXMLStreamWriter(output);
		}
	}

	public static class XML extends ResponseFormatter<XMLStreamWriter> {
		private final XMLOutputFactory factory;
		private final boolean prettyPrint;
		
		public XML(String apiVersion, LocalStrings localStrings, boolean noDirectoryIndex, boolean includeUnknownGenre, boolean useGrouping, boolean prettyPrint) {
			super(apiVersion, localStrings, noDirectoryIndex ? null : "index.xml", includeUnknownGenre, useGrouping);
			factory = XMLOutputFactory.newFactory();
			this.prettyPrint = prettyPrint;
		}

		void writeNumberProperty(XMLStreamWriter writer, String name, Number value) throws XMLStreamException {
			writeStringProperty(writer, name, value.toString());
		}

		void writeStartArray(XMLStreamWriter writer) throws XMLStreamException {
			// do nothing
		}

		XMLStreamWriter createStreamWriter(OutputStream output) throws XMLStreamException {
			XMLStreamWriter writer = factory.createXMLStreamWriter(output);
			if (prettyPrint) {
				writer = new PrettyXMLStreamWriter(writer);
			}
			return writer;
		}
	}

	private final String apiVersion;
	private final LocalStrings localStrings;
	private final String directoryIndex;
	private final boolean includeUnknownGenre;
	private final boolean useGrouping;

	private final String updateToken = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
	
	ResponseFormatter(String apiVersion, LocalStrings localStrings, String directoryIndex, boolean includeUnknownGenre, boolean useGrouping) {
		this.apiVersion = apiVersion;
		this.localStrings = localStrings;
		this.directoryIndex = directoryIndex;
		this.includeUnknownGenre = includeUnknownGenre;
		this.useGrouping = useGrouping;
	}
	
	void writeStringProperty(T writer, String name, String value) throws XMLStreamException {
		writer.writeStartElement(name);
		writer.writeCharacters(value);
		writer.writeEndElement();
	}
	
	void startResponse(T writer, String contentElement) throws XMLStreamException {
		writer.writeStartDocument();
		writer.writeStartElement("response");
		writeStringProperty(writer, "apiVersion", apiVersion);
		writeStringProperty(writer, "updateToken", updateToken);
		writer.writeStartElement(contentElement);
	}

	void endResponse(T writer) throws XMLStreamException {
		writer.writeEndElement(); // <content element>
		writer.writeEndElement(); // response
		writer.writeEndDocument();
		writer.close();
	}

	abstract void writeNumberProperty(T writer, String name, Number value) throws XMLStreamException;

	abstract void writeStartArray(T writer) throws XMLStreamException;

	abstract T createStreamWriter(OutputStream output) throws XMLStreamException;

	private String getDocumentPath(String path) {
		if (directoryIndex == null || path.length() <= directoryIndex.length()) {
			return path;
		}
		int slash = path.length() - directoryIndex.length() - 1;
		if (path.charAt(slash) != '/' || !path.endsWith(directoryIndex)) {
			return path;
		}
		return path.substring(0, slash + 1);
	}

	private List<String> genreList(Playlist playlist) {
		// collect genre counts
		final Map<String, Integer> map = new HashMap<String, Integer>();
		int unknownCount = 0;
		for (Track track : playlist.getTracks()) {
			String genre = track.getGenre();
			if (genre == null) {
				unknownCount++;
			} else {
				Integer count = map.get(genre);
				map.put(genre, Integer.valueOf(count == null ? 1 : count + 1));
			}
		}
		if (includeUnknownGenre && unknownCount > 0) {
			map.put(localStrings.getUnknownGenre(), unknownCount);
		}
		// sort decreasing by counts
		ArrayList<String> result = new ArrayList<String>(map.keySet());
		Collections.sort(result, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				int cmp = map.get(o2).compareTo(map.get(o1));
				if (cmp == 0) {
					cmp = o1.compareTo(o2);
				}
				return cmp;
			}
		});
		return result;
	}

	private List<String> genreList(Artist artist) {
		Playlist playlist = new Playlist();
		for (Album album : artist.albums()) {
			if (artist.getArtistType() == ArtistType.TrackArtist) {
				for (Track track : album.getTracks()) {
					if (track.getArtist() == artist) {
						playlist.getTracks().add(track);
					}
				}
			} else { // take all tracks into account
				playlist.getTracks().addAll(album.getTracks());
			}
		}
		return genreList(playlist);
	}

	private String getDefaultArtistTitle(ArtistType artistType) {
		return artistType == ArtistType.AlbumArtist ? localStrings.getVariousArtists() : localStrings.getUnknownArtist();
	}

	private String getDefaultAlbumTitle() {
		return localStrings.getUnknownAlbum();
	}

	private String trackTitle(Track track) {
		if (useGrouping && track.getGrouping() != null && track.getTitle().startsWith(track.getGrouping())) {
			String title = track.getTitle().substring(track.getGrouping().length());
			if (title.length() > 0) {
				if (Character.isAlphabetic(title.charAt(0)) || Character.isDigit(title.charAt(0))) {
					return title;
				}
				for (int start = 1; start < title.length(); start++) {
					if (Character.isAlphabetic(title.charAt(start)) || Character.isDigit(title.charAt(start))) {
						return title.substring(start);
					}
				}
			}
		}
		return track.getTitle();
	}
	
	public Integer albumYear(Album album, Integer defaultValue) {
		Integer maximumYear = null;
		for (Track track : album.getTracks()) {
			if (maximumYear == null || track.getYear() != null && maximumYear.compareTo(track.getYear()) < 0) {
				maximumYear = track.getYear();
			}
		}
		return maximumYear != null ? maximumYear : defaultValue;
	}
	
	private void formatArtistSections(T writer, Iterable<CollectionSection<Artist>> sections, ResourceLocator resourceLocator, ImageType imageType, ArtistType artistType, Map<Artist, Album> representativeAlbums) throws Exception {
		writeStartArray(writer);
		for (CollectionSection<Artist> section : sections) {
			writer.writeStartElement("section");
			if (section.getTitle() != null) {
				writeStringProperty(writer, "title", section.getTitle());
			}
			writeStartArray(writer);
			for (Artist item : section.getItems()) {
				writer.writeStartElement("item");
				writeStringProperty(writer, "title", item.getTitle() == null ? getDefaultArtistTitle(artistType) : item.getTitle());
				Album representativeAlbum = representativeAlbums != null ? representativeAlbums.get(item) : null;
				String imagePath = representativeAlbum != null ? resourceLocator.getAlbumImagePath(representativeAlbum, imageType) : null;
				if (imagePath != null && resourceLocator.getFile(imagePath).exists()) {
					writeStringProperty(writer, "imagePath", imagePath);
				}
				writeStringProperty(writer, "albumCollectionPath", getDocumentPath(resourceLocator.getAlbumCollectionPath(item)));
				List<String> genreList = genreList(item);
				if (genreList.size() > 0) {
					writeStartArray(writer);
					for (String genre : genreList) {
						writeStringProperty(writer, "genre", genre);
					}
				}
				writeNumberProperty(writer, "albumCount", item.albumsCount());					
				writer.writeEndElement();
			}
			writer.writeEndElement();
		}
	}

	private void formatAlbumSections(T writer, Iterable<CollectionSection<Album>> sections, ResourceLocator resourceLocator, ImageType imageType, boolean writeCompilationInfo) throws Exception {
		writeStartArray(writer);
		for (CollectionSection<Album> section : sections) {
			writer.writeStartElement("section");
			if (section.getTitle() != null) {
				writeStringProperty(writer, "title", section.getTitle());
			}
			writeStartArray(writer);
			for (Album item : section.getItems()) {
				writer.writeStartElement("item");
				writeStringProperty(writer, "title", item.getTitle() == null ? getDefaultAlbumTitle() : item.getTitle());
				String imagePath = resourceLocator.getAlbumImagePath(item, imageType);
				if (imagePath != null && resourceLocator.getFile(imagePath).exists()) {
					writeStringProperty(writer, "imagePath", imagePath);
				}
				if (writeCompilationInfo && item.isCompilation() && item.getArtist().getTitle() != null) {
					writeStringProperty(writer, "info", localStrings.getCompilation());
				}
				writeStringProperty(writer, "artist", item.getArtist().getTitle() == null ? getDefaultArtistTitle(ArtistType.AlbumArtist) : item.getArtist().getTitle());
				writeStringProperty(writer, "albumPath", getDocumentPath(resourceLocator.getAlbumPath(item)));
				List<String> genreList = genreList(item);
				if (genreList.size() > 0) {
					writeStartArray(writer);
					for (String genre : genreList(item)) {
						writeStringProperty(writer, "genre", genre);
					}
				}
				Integer year = albumYear(item, null);
				if (year != null) {
					writeNumberProperty(writer, "year", year);					
				}
				writer.writeEndElement();
			}
			writer.writeEndElement();
		}
	}

	private Iterable<CollectionSection<Album>> createAlbumCollectionSections(Artist artist) {
		// split albums into sections with regular albums and others (compilations/various/unknown)
		CollectionSection<Album> regularAlbums = new CollectionSection<Album>(localStrings.getRegularAlbumSection());
		CollectionSection<Album> otherAlbums = new CollectionSection<Album>(artist.getTitle() != null ?localStrings.getCompilationAlbumSection() : null);
		for (Album album : artist.albums()) {
			if (artist.getTitle() != null) {
				if (album.isCompilation()) {
					otherAlbums.getItems().add(album);
				} else {
					regularAlbums.getItems().add(album);
				}
			} else {
				otherAlbums.getItems().add(album);
			}
		}

		Collection<CollectionSection<Album>> sections = new ArrayList<CollectionSection<Album>>();

		final Comparator<Album> titleComparator = new TitledComparator<Album>(localStrings, getDefaultAlbumTitle(), null);

		if (!regularAlbums.getItems().isEmpty()) {
			// sort regular albums by year and title
			Collections.sort(regularAlbums.getItems(), new Comparator<Album>() {
				@Override
				public int compare(Album item1, Album item2) {
					int result = albumYear(item1, Integer.MAX_VALUE).compareTo(albumYear(item2, Integer.MAX_VALUE));
					if (result != 0) {
						return result;
					}
					return titleComparator.compare(item1, item2);
				}
			});
			sections.add(regularAlbums);
		}
		
		if (!otherAlbums.getItems().isEmpty()) {
			// sort other albums by title only
			Collections.sort(otherAlbums.getItems(), titleComparator);
			sections.add(otherAlbums);
		}
		
		return sections;
	}

	public void formatServiceIndex(ResourceLocator resourceLocator, OutputStream output) throws Exception {
		T writer = createStreamWriter(output);
		startResponse(writer, "serviceIndex");
		String albumArtistIndexPath = resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist);
		if (albumArtistIndexPath != null) {
			writeStringProperty(writer, "albumArtistIndexPath", getDocumentPath(albumArtistIndexPath));
		}
		String trackArtistIndexPath = resourceLocator.getArtistIndexPath(ArtistType.TrackArtist);
		if (trackArtistIndexPath != null) {
			writeStringProperty(writer, "artistIndexPath", getDocumentPath(trackArtistIndexPath));
		}
		String albumIndexPath = resourceLocator.getAlbumIndexPath();
		if (albumIndexPath != null) {
			writeStringProperty(writer, "albumIndexPath", getDocumentPath(albumIndexPath));
		}
		endResponse(writer);
	}

	public void formatArtistIndex(Collection<? extends Artist> artists, ArtistType artistType, OutputStream output, ResourceLocator resourceLocator, Map<Artist, Album> representativeAlbums) throws Exception {
		T writer = createStreamWriter(output);
		startResponse(writer, "artistCollection");
		writeStringProperty(writer, "title", localStrings.getArtistIndexTitle(artistType));
		TitledComparator<Artist> comparator = new TitledComparator<Artist>(localStrings, getDefaultArtistTitle(artistType), new Comparator<Artist>() {
			@Override
			public int compare(Artist o1, Artist o2) { // sort equally titled artists descending by album count
				return -Integer.valueOf(o1.albumsCount()).compareTo(Integer.valueOf(o2.albumsCount()));
			}
		});
		Iterable<CollectionSection<Artist>> sections = CollectionSection.createIndex(artists, comparator);
		formatArtistSections(writer, sections, resourceLocator, ImageType.Thumbnail, artistType, representativeAlbums);
		endResponse(writer);
	}
	
	public void formatAlbumIndex(Collection<Album> albums, OutputStream output, ResourceLocator resourceLocator) throws Exception {
		T writer = createStreamWriter(output);
		startResponse(writer, "albumCollection");
		writeStringProperty(writer, "title", "Albums");
		TitledComparator<Album> comparator = new TitledComparator<Album>(localStrings, getDefaultAlbumTitle(), new Comparator<Album>() {
			@Override
			public int compare(Album o1, Album o2) { // sort equally titled albums by album artist
				String title1 = o1.getArtist().getTitle() == null ? getDefaultArtistTitle(ArtistType.AlbumArtist) : o1.getArtist().getTitle();
				String title2 = o2.getArtist().getTitle() == null ? getDefaultArtistTitle(ArtistType.AlbumArtist) : o2.getArtist().getTitle();
				return title1.compareTo(title2);
			}
		});
		Iterable<CollectionSection<Album>> sections = CollectionSection.createIndex(albums, comparator);
		formatAlbumSections(writer, sections, resourceLocator, ImageType.Thumbnail, true);
		endResponse(writer);
	}

	/**
	 * 
	 * @param artist
	 * @param output
	 * @param resourceLocator
	 * @return representative album
	 * @throws Exception
	 */
	public Album formatAlbumCollection(Artist artist, OutputStream output, ResourceLocator resourceLocator) throws Exception {
		String title = artist.getTitle() == null ? getDefaultArtistTitle(artist.getArtistType()) : artist.getTitle();
		Iterable<CollectionSection<Album>> sections = createAlbumCollectionSections(artist);

		T writer = createStreamWriter(output);
		startResponse(writer, "albumCollection");
		writeStringProperty(writer, "title", title);
		formatAlbumSections(writer, sections, resourceLocator, ImageType.Tile, false);
		endResponse(writer);
		
		// answer first album with an associated artist
		for (CollectionSection<Album> section : sections) {
			for (Album album : section.getItems()) {
				if (album.getArtist().getTitle() != null) {
					return album;
				}
			}
		}
		return null; // no representative album for unknown/various artist
	}

	public void formatAlbum(Album album, OutputStream output, ResourceLocator resourceLocator, AssetLocator assetLocator) throws Exception {
		T writer = createStreamWriter(output);
		startResponse(writer, "album");
		writeStringProperty(writer, "title", album.getTitle() == null ? getDefaultAlbumTitle() : album.getTitle());
		if (album.isCompilation() && album.getArtist().getTitle() != null) {
			writeStringProperty(writer, "info", localStrings.getCompilation());
		}
		writeStringProperty(writer, "artist", album.getArtist().getTitle() == null ? getDefaultArtistTitle(ArtistType.AlbumArtist) : album.getArtist().getTitle());
//		writeStringProperty(writer, "albumPath", resourceLocator.getAlbumPath(album));
		List<String> genreList = genreList(album);
		if (genreList.size() > 0) {
			writeStartArray(writer);
			for (String genre : genreList(album)) {
				writeStringProperty(writer, "genre", genre);
			}
		}
		Integer year = albumYear(album, null);
		if (year != null) {
			writeNumberProperty(writer, "year", year);					
		}
		String albumImagePath = resourceLocator.getAlbumImagePath(album, ImageType.Artwork);
		if (albumImagePath != null && resourceLocator.getFile(albumImagePath).exists()) {
			writeStringProperty(writer, "imagePath", albumImagePath);
		}

		/*
		 * format tracks
		 */
		String trackImagePath = resourceLocator.getAlbumImagePath(album, ImageType.Thumbnail);
		if (trackImagePath != null && !resourceLocator.getFile(trackImagePath).exists()) {
			trackImagePath = null;
		}
		writer.writeStartElement("trackCollection");
		writeStringProperty(writer, "title", "Tracks");
		writeStartArray(writer);
		for (Disc disc : album.getDiscs().values()) {
			writer.writeStartElement("section");
			if (disc.getDiscNumber() == 0 || disc.getDiscNumber() == 1 && album.getDiscs().size() == 1) {
				writeStringProperty(writer, "title", localStrings.getTracks());
			} else {
				writeStringProperty(writer, "title", String.format("%s %d", localStrings.getDisc(), disc.getDiscNumber()));
			}
			ArrayList<Track> items = new ArrayList<Track>(disc.getTracks());
			Collections.sort(items, new Comparator<Track>() {
				public int compare(Track t1, Track t2) {
					if (t1.getTrackNumber() == null && t2.getTrackNumber() == null) {
						return 0;
					} else if (t1.getTrackNumber() == null) {
						return -1;
					} else if (t2.getTrackNumber() == null) {
						return +1;
					}
					return t1.getTrackNumber().compareTo(t2.getTrackNumber());
				}
			});
			writeStartArray(writer);
			for (Track item : items) {
				writer.writeStartElement("item");
				writeStringProperty(writer, "title", trackTitle(item));
				String artist = item.getArtist().getTitle();
				if (artist == null) {
					artist = localStrings.getUnknownArtist();
				}
				writeStringProperty(writer, "artist", artist);
				if (trackImagePath != null) {
					writeStringProperty(writer, "imagePath", trackImagePath);
				}
				if (item.getGenre() != null) {
					writeStringProperty(writer, "genre", item.getGenre());
				}
				if (item.getGrouping() != null && useGrouping) {
					writeStringProperty(writer, "grouping", item.getGrouping());
				}
				if (item.getComposer() != null) {
					writeStringProperty(writer, "composer", item.getComposer());
				}
				if (item.getTrackNumber() != null) {
					writeNumberProperty(writer, "trackNumber", item.getTrackNumber());
				}
				if (item.getDuration() != null) {
					writeNumberProperty(writer, "duration", item.getDuration());
				}
				String assetPath = assetLocator.getAssetPath(item.getAssetFile());
				if (assetPath != null) {
					writeStringProperty(writer, "assetPath", assetPath);
				}
				writer.writeEndElement();
			}
			writer.writeEndElement(); // section
		}
		writer.writeEndElement(); // trackCollection
		endResponse(writer);
	}
}
