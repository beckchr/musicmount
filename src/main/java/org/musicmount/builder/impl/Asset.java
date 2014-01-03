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

import java.io.File;

public class Asset {
	private String name;
	private String artist;
	private String albumArtist;
	private String album;
	private String genre;
	private String composer;
	private Integer duration;
	private String grouping;
	private Integer discNumber;
	private Integer trackNumber;
	private Integer year;
	private boolean compilation;
	private boolean artworkAvailable;

	private final File file;

	public Asset(File file) {
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public String getArtist() {
		return artist;
	}
	public void setArtist(String artist) {
		this.artist = artist;
	}

	public String getAlbumArtist() {
		return albumArtist;
	}
	public void setAlbumArtist(String albumArtist) {
		this.albumArtist = albumArtist;
	}

	public String getAlbum() {
		return album;
	}
	public void setAlbum(String album) {
		this.album = album;
	}

	public String getGenre() {
		return genre;
	}
	public void setGenre(String genre) {
		this.genre = genre;
	}

	public String getComposer() {
		return composer;
	}	
	public void setComposer(String info) {
		this.composer = info;
	}	

	public Integer getDuration() {
		return duration;
	}
	public void setDuration(Integer duration) {
		this.duration = duration;
	}
	
	public String getGrouping() {
		return grouping;
	}
	public void setGrouping(String grouping) {
		this.grouping = grouping;
	}

	public Integer getDiscNumber() {
		return discNumber;
	}
	public void setDiscNumber(Integer discNumber) {
		this.discNumber = discNumber;
	}

	public Integer getTrackNumber() {
		return trackNumber;
	}
	public void setTrackNumber(Integer trackNumber) {
		this.trackNumber = trackNumber;
	}

	public Integer getYear() {
		return year;
	}
	public void setYear(Integer year) {
		this.year = year;
	}

	public boolean isCompilation() {
		return compilation;
	}
	public void setCompilation(boolean compilation) {
		this.compilation = compilation;
	}
	
	public boolean isArtworkAvailable() {
		return artworkAvailable;
	}
	public void setArtworkAvailable(boolean artworkAvailable) {
		this.artworkAvailable = artworkAvailable;
	}
	
	@Override
	public String toString() {
		return "Asset(" + getFile() + ")";
	}
}
