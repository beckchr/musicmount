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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Library extends Playlist {
	private final Map<String, AlbumArtist> albumArtists = new LinkedHashMap<String, AlbumArtist>();
	private final Map<String, TrackArtist> trackArtists = new LinkedHashMap<String, TrackArtist>();
	private final List<Album> albums = new ArrayList<Album>();
	
	public Map<String, AlbumArtist> getAlbumArtists() {
		return albumArtists;
	}
	
	public Map<String, TrackArtist> getTrackArtists() {
		return trackArtists;
	}
	
	public List<Album> getAlbums() {
		return albums;
	}
}
