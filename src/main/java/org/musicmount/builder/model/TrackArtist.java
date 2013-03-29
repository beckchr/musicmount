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

import java.util.LinkedHashSet;
import java.util.Set;

public class TrackArtist extends Artist {
	private final Set<Album> albums = new LinkedHashSet<Album>();

	public TrackArtist(long id, String title) {
		super(id, title, ArtistType.TrackArtist);
	}
	
	@Override
	public Iterable<Album> albums() {
		return albums;
	}
	
	public Set<Album> getAlbums() {
		return albums;
	}
	
	@Override
	public int albumsCount() {
		return albums.size();
	}
	
	@Override
	public String toString() {
		return "TrackArtist(" + getTitle() + ")";
	}
}
