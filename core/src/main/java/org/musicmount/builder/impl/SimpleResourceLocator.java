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

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.io.Resource;

public class SimpleResourceLocator implements ResourceLocator {
	private final Resource outputFolder;
	private final String extension;
	private final boolean noImages;
	private final boolean noTrackIndex;
	
	public SimpleResourceLocator(Resource outputFolder, boolean xml, boolean noImages, boolean noTrackIndex) {
		this.outputFolder = outputFolder;
		this.extension = xml ? "xml" : "json";
		this.noImages = noImages;
		this.noTrackIndex = noTrackIndex;
	}		

	private String getArtistPathPrefix(ArtistType artistType) {
		switch (artistType) {
		case AlbumArtist:
			return "albumArtists";
		case TrackArtist:
			return "artists";
		}
		return null;
	}

	private String getIdPath(long id) {
		/*
		 * split id into "xx/xx" hex digit path.
		 * For id larger than 0xffff, the front segment will grow, i.e xxx/xx, ...  
		 */
		String numberString = String.format("%04x", id);
		int splitIndex = numberString.length() - 2;
		return String.format("%s/%s", numberString.substring(0, splitIndex), numberString.substring(splitIndex, numberString.length()));
	}

	@Override
	public Resource getResource(String path) {
		return outputFolder.resolve(path);
	}

	@Override
	public String getServiceIndexPath() {
		return String.format("index.%s", extension);
	}

	@Override
	public String getAlbumIndexPath() {
		return String.format("albums/index.%s", extension);
	}

	@Override
	public String getTrackIndexPath() {
		return noTrackIndex ? null : String.format("tracks/index.%s", extension);
	}

	@Override
	public String getArtistIndexPath(ArtistType artistType) {
		return String.format("%s/index.%s", getArtistPathPrefix(artistType), extension);
	}
	
	@Override
	public String getAlbumCollectionPath(Artist artist) {
		return String.format("%s/%s-albums.%s", getArtistPathPrefix(artist.getArtistType()), getIdPath(artist.getArtistId()), extension);
	}

	@Override
	public String getAlbumImagePath(Album album, ImageType type) {
		if (noImages || album.artworkAssetResource() == null) {
			return null;
		}
		return String.format("albums/%s/%s", getIdPath(album.getAlbumId()), type.getFileName());
	}

	@Override
	public String getAlbumPath(Album album) {
		return String.format("albums/%s/album.%s", getIdPath(album.getAlbumId()), extension);
	}
}