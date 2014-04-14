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

import org.musicmount.io.Resource;

public class Track implements Titled {
	private final String title;
	private final Resource resource;
	private final boolean artworkAvailable;
	private final boolean compilation;
	private final String composer;
	private final Integer discNumber;
	private final Integer duration;
	private final String genre;
	private final String grouping;
	private final Integer trackNumber;
	private final Integer year;

	private TrackArtist artist;
	private Album album;

	public Track(
			String title,
			Resource resource,
			boolean artworkAvailable,
			boolean compilation,
			String composer,
			Integer discNumber,
			Integer duration,
			String genre,
			String grouping,
			Integer trackNumber,
			Integer year
	) {
		this.title = title;
		this.resource = resource;
		this.artworkAvailable = artworkAvailable;
		this.compilation = compilation;
		this.composer = composer;
		this.discNumber = discNumber;
		this.duration = duration;
		this.genre = genre;
		this.grouping = grouping;
		this.trackNumber = trackNumber;
		this.year = year;
	}
	
	public Resource getResource() {
		return resource;
	}
	
	@Override
	public String getTitle() {
		return title;
	}

	public String getGenre() {
		return genre;
	}
	
	public String getGrouping() {
		return grouping;
	}

	public String getComposer() {
		return composer;
	}	

	public Integer getDuration() {
		return duration;
	}

	public Integer getDiscNumber() {
		return discNumber;
	}

	public Integer getTrackNumber() {
		return trackNumber;
	}

	public Integer getYear() {
		return year;
	}

	public boolean isCompilation() {
		return compilation;
	}
	
	public boolean isArtworkAvailable() {
		return artworkAvailable;
	}

	public TrackArtist getArtist() {
		return artist;
	}
	public void setArtist(TrackArtist artist) {
		this.artist = artist;
	}
	
	public Album getAlbum() {
		return album;
	}
	public void setAlbum(Album album) {
		this.album = album;
	}
	
	@Override
	public String toString() {
		return "Track(" + getTitle() + ")";
	}
}
