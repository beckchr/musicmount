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

import java.io.File;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Album extends Playlist implements Titled {
	private final String title;
	private final SortedMap<Integer, Disc> discs = new TreeMap<Integer, Disc>();
	
	private AlbumArtist artist;
	private long albumId;
	
	public Album(String title) {
		this.title = title;
	}
	
	public Map<Integer, Disc> getDiscs() {
		return discs;
	}
	
	public long getAlbumId() {
		return albumId;
	}
	
	public void setAlbumId(long albumId) {
		this.albumId = albumId;
	}
	
	public String getTitle() {
		return title;
	}
	
	public AlbumArtist getArtist() {
		return artist;
	}
	
	public void setArtist(AlbumArtist artist) {
		this.artist = artist;
	}
	
	public boolean isCompilation() {
		return getTracks().get(0).isCompilation();
	}

	public File artworkAssetFile() {
		for (Track track : getTracks()) {
			if (track.isArtworkAvailable()) {
				return track.getAssetFile();
			}
		}
		return null;
	}
	
	@Override
	public String toString() {
		return "Album(" + getTitle() + ")";
	}
}
