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

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Album extends Playlist implements Titled {
	private final long albumId;
	private final String title;
	private final SortedMap<Integer, Disc> discs = new TreeMap<Integer, Disc>();
	
	private AlbumArtist albumArtist;
	
	public Album(long albumId, String title) {
		this.albumId = albumId;
		this.title = title;
	}
	
	public Map<Integer, Disc> getDiscs() {
		return discs;
	}
	
	public long getAlbumId() {
		return albumId;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String sortTitle() {
		return title;
	}
	
	public AlbumArtist getAlbumArtist() {
		return albumArtist;
	}
	
	public void setAlbumArtist(AlbumArtist albumArtist) {
		this.albumArtist = albumArtist;
	}
	
	public Track representativeTrack() {
		Track anyTrack = null;
		for (Track track : getTracks()) {
			if (track.isArtworkAvailable()) {
				return track;
			}
			anyTrack = track;
		}
		return anyTrack;
	}
}
