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
}
