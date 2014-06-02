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
package org.musicmount.live;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.xml.stream.XMLStreamException;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.ImageType;
import org.musicmount.builder.impl.ResourceLocator;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.AlbumArtist;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Library;
import org.musicmount.builder.model.TrackArtist;
import org.musicmount.io.Resource;

public class LiveMount {

	private final Library library;
	private final ResponseFormatter<?> responseFormatter;
	private final ImageFormatter imageFormatter;
	private final AssetLocator assetLocator;
	private final boolean noTrackIndex;

	private final Map<Long, Album> albumLookup;
	private final Map<Long, AlbumArtist> albumArtistLookup;
	private final Map<Long, TrackArtist> trackArtistLookup;
	private final Map<Artist, Album> representativeAlbums;

	public LiveMount(Library library, ResponseFormatter<?> responseFormatter, ImageFormatter imageFormatter, AssetLocator assetLocator, boolean noTrackIndex) {
		this.library = library;
		this.responseFormatter = responseFormatter;
		this.imageFormatter = imageFormatter;
		this.assetLocator = assetLocator;
		this.noTrackIndex = noTrackIndex;

		this.albumArtistLookup = new HashMap<>();
		this.trackArtistLookup = new HashMap<>();
		this.albumLookup = new HashMap<>();
		this.representativeAlbums = new HashMap<>();

		for (Album album : library.getAlbums()) {
			albumLookup.put(album.getAlbumId(), album);
		}
		for (AlbumArtist albumArtist : library.getAlbumArtists().values()) {
			albumArtistLookup.put(albumArtist.getArtistId(), albumArtist);
			representativeAlbums.put(albumArtist, albumArtist.albums().iterator().next());
		}
		for (TrackArtist trackArtist : library.getTrackArtists().values()) {
			trackArtistLookup.put(trackArtist.getArtistId(), trackArtist);
			representativeAlbums.put(trackArtist, trackArtist.albums().iterator().next());
		}
	}

	public Album getAlbum(Long albumId) {
		return albumLookup.get(albumId);
	}
	
	private Iterable<? extends Artist> getArtists(ArtistType artistType) {
		switch (artistType) {
		case AlbumArtist:
			return library.getAlbumArtists().values();
		case TrackArtist:
			return library.getTrackArtists().values();
		default:
			return Collections.emptyList();
		}
	}
	
	public Artist getArtist(ArtistType artistType, Long artistId) {
		if (artistType == null) {
			return null;
		}
		switch (artistType) {
		case AlbumArtist:
			return albumArtistLookup.get(artistId);
		case TrackArtist:
			return trackArtistLookup.get(artistId);
		default:
			return null;
		}
	}
	
	public boolean isNoTrackIndex() {
		return noTrackIndex;
	}
	
	public void formatServiceIndex(ResourceLocator resourceLocator, OutputStream output) throws IOException, ServletException {
		try {
			responseFormatter.formatServiceIndex(resourceLocator, output);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}

	public void formatAlbumIndex(ResourceLocator resourceLocator, OutputStream output) throws IOException, ServletException {
		try {
			responseFormatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}

	public void formatArtistIndex(ResourceLocator resourceLocator, OutputStream output, ArtistType artistType) throws IOException, ServletException {
		try {
			responseFormatter.formatArtistIndex(getArtists(artistType), artistType, output, resourceLocator, representativeAlbums);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}
	
	public void formatTrackIndex(ResourceLocator resourceLocator, OutputStream output) throws IOException, ServletException {
		try {
			responseFormatter.formatTrackIndex(library.getTracks(), output, resourceLocator, null);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}
	
	public void formatAlbumCollection(ResourceLocator resourceLocator, OutputStream output, Artist artist) throws IOException, ServletException {
		try {
			responseFormatter.formatAlbumCollection(artist, output, resourceLocator);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}

	public void formatAlbum(ResourceLocator resourceLocator, OutputStream output, Album album) throws IOException, ServletException {
		try {
			responseFormatter.formatAlbum(album, output, resourceLocator, assetLocator);
		} catch (XMLStreamException e) {
			throw new ServletException(e);
		}
	}
	
	public void formatImage(Resource assetResource, OutputStream output, ImageType type) throws IOException {
		imageFormatter.formatAsset(assetResource, type, output);
	}
}
