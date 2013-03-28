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
package org.musicmount.builder.impl;

import java.util.Locale;
import java.util.ResourceBundle;

import org.musicmount.builder.model.ArtistType;

public class LocalStrings {
	private final ResourceBundle bundle;
	
	public LocalStrings() {
		this(Locale.ENGLISH);
	}
	
	public LocalStrings(Locale locale) {
		this.bundle = ResourceBundle.getBundle(getClass().getName(), locale);
	}
	
	public Locale getLocale() {
		return bundle.getLocale();
	}
	
	public String getCompilationAlbumSection() {
		return bundle.getString("CompilationAlbumSection");
	}

	public String getRegularAlbumSection() {
		return bundle.getString("RegularAlbumSection");
	}

	public String getCompilation() {
		return bundle.getString("Compilation");
	}

	public String getVariousArtists() {
		return bundle.getString("VariousArtists");
	}

	public String getUnknownGenre() {
		return bundle.getString("UnknownGenre");
	}
	
	public String getUnknownArtist() {
		return bundle.getString("UnknownArtist");
	}
	
	public String getUnknownAlbum() {
		return bundle.getString("UnknownAlbum");
	}
	
	public String getDisc() {
		return bundle.getString("Disc");
	}
	
	public String getArtistIndexTitle(ArtistType artistType) {
		return bundle.getString(artistType == ArtistType.AlbumArtist ? "AlbumArtistIndexTitle" : "TrackArtistIndexTitle");
	}

	public String getAlbumIndexTitle() {
		return bundle.getString("AlbumIndexTitle");
	}
	
	public String[] getSortTitlePrefixes() {
		return bundle.getString("SortTitlePrefixes").split(",\\s*");
	}
}
