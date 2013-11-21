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
package org.musicmount.builder.model;


public abstract class Artist implements Titled {
	private final long artistId;
	private final String title;
	private final ArtistType artistType;

	public Artist(long id, String title, ArtistType artistType) {
		this.artistId = id;
		this.title = title;
		this.artistType = artistType;
	}
	
	public abstract Iterable<Album> albums();
	public abstract int albumsCount();
	
	public long getArtistId() {
		return artistId;
	}
	
	public ArtistType getArtistType() {
		return artistType;
	}

	public String getTitle() {
		return title;
	}

	private Album earliest(Album album1, Album album2) {
		if (album1 == null || album1.getTracks().get(0).getYear() == null) {
			return album2 == null ? album1 : album2;
		}
		if (album2 == null || album2.getTracks().get(0).getYear() == null) {
			return album1;
		}
		int year1 = album1.getTracks().get(0).getYear().intValue();
		int year2 = album2.getTracks().get(0).getYear().intValue();
		if (year1 == year2) { // compare album titles
			if (album1.getTitle() == null) {
				return album2;
			}
			if (album2.getTitle() == null) {
				return album1;
			}
			return album1.getTitle().compareTo(album2.getTitle()) < 0 ? album1 : album2;
		}
		return year1 < year2 ? album1 : album2;
	}
	
	public Album representativeAlbum() {
		// pick album with artwork, prefer regular album, newer album
		Album regularAlbum = null;
		Album compilation = null;
		Album otherAlbum = null;
		for (Album album : albums()) {
			Track representativeTrack = album.representativeTrack();
			if (representativeTrack.isArtworkAvailable())  {
				if (representativeTrack.isCompilation()) {
					compilation = earliest(album, compilation);
				} else {
					regularAlbum = earliest(album, regularAlbum);
				}
			} else {
				otherAlbum = earliest(album, otherAlbum);
			}
		}
		return regularAlbum != null ? regularAlbum : (compilation != null ? compilation : otherAlbum);
	}
}
