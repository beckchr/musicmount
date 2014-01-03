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
package org.musicmount.builder.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class AlbumArtist extends Artist {
	private final Map<String, Album> albums = new LinkedHashMap<String, Album>();

	public AlbumArtist(long id, String title) {
		super(id, title, ArtistType.AlbumArtist);
	}
	
	@Override
	public Collection<Album> albums() {
		return albums.values();
	}
	
	public Map<String, Album> getAlbums() {
		return albums;
	}
	
	@Override
	public int albumsCount() {
		return albums.size();
	}
	
	@Override
	public String toString() {
		return "AlbumArtist(" + getTitle() + ")";
	}
}
