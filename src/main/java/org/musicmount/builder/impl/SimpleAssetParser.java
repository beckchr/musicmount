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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;
import org.jaudiotagger.tag.reference.GenreTypes;
import org.musicmount.builder.model.Track;
import org.musicmount.util.LoggingUtil;

public class SimpleAssetParser implements AssetParser {
	static final Logger LOGGER = Logger.getLogger(SimpleAssetParser.class.getName());
	static {
		LoggingUtil.configure("org.jaudiotagger", Level.WARNING);
	}

	private final Collection<String> audioExtensions;

	public SimpleAssetParser() {
		this(Arrays.asList(".m4a", ".mp3"));
	}

	public SimpleAssetParser(Collection<String> audioExtensions) {
		this.audioExtensions = audioExtensions;
	}
	
	@Override
	public boolean isAssetFile(File file) {
		for (String extension : audioExtensions) {
			if (file.getName().endsWith(extension)) {
				return true;
			}
		}
		return false;
	}
	
	public Track parse(File assetFile) throws Exception {
		Track track = new Track(assetFile);

		AudioFile audioFile = AudioFileIO.read(assetFile);

		int trackLength = audioFile.getAudioHeader().getTrackLength();
		track.setDuration(trackLength > 0 ? trackLength : null);
		
		Tag tag = audioFile.getTag();

		String artist = tag.getFirst(FieldKey.ARTIST);
		if (artist != null && artist.trim().length() > 0) {
			track.setArtist(artist.trim());
		}
		
		String albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST);
		if (albumArtist != null && albumArtist.trim().length() > 0) {
			track.setAlbumArtist(albumArtist.trim());
		}

		String album = tag.getFirst(FieldKey.ALBUM);
		if (album != null && album.trim().length() > 0) {
			track.setAlbum(album.trim());
		}

		String title = tag.getFirst(FieldKey.TITLE);
		if (title != null && title.trim().length() > 0) {
			track.setName(title.trim());
		}
		
		String composer = tag.getFirst(FieldKey.COMPOSER);
		if (composer != null && composer.trim().length() > 0) {
			track.setComposer(composer.trim());
		}
		
		String genre = tag.getFirst(FieldKey.GENRE);
		if (genre != null && genre.trim().length() > 0) {
			track.setGenre(genre.trim());
			boolean mp3 = audioFile.getFile().getName().endsWith(".mp3"); // replace MP3 genre id
			if (mp3 && track.getGenre().matches("\\(\\d+\\).*")) { // e.g. "(13)" or "(13)Pop"
				int id = Integer.valueOf(track.getGenre().substring(1, track.getGenre().indexOf(')')));
				String mp3genre = GenreTypes.getInstanceOf().getValueForId(id);
				if (mp3genre != null) {
					track.setGenre(mp3genre);
				}
			}
		}
		
		String year = tag.getFirst(FieldKey.YEAR);
		if (year != null && year.trim().length() >= 4) {
			try {
				track.setYear(Integer.valueOf(year.trim().substring(0, 4)));
			} catch (NumberFormatException e) {
				LOGGER.log(Level.FINE, "Could not parse year: " + year + ", (" + track.getAssetFile().getAbsolutePath() + ")", e);
			}
		}
		
		String trackNumber = tag.getFirst(FieldKey.TRACK);
		if (trackNumber != null && trackNumber.trim().length() > 0) {
			try {
				track.setTrackNumber(Integer.valueOf(trackNumber));			
			} catch (NumberFormatException e) {
				LOGGER.log(Level.FINE, "Could not parse track number: " + trackNumber + ", (" + track.getAssetFile().getAbsolutePath() + ")", e);
			}
		}

		String discNumber = tag.getFirst(FieldKey.DISC_NO);		
		if (discNumber != null && discNumber.trim().length() > 0) {
			try {
				track.setDiscNumber(Integer.valueOf(discNumber));
			} catch (NumberFormatException e) {
				LOGGER.log(Level.FINE, "Could not parse disc number: " + discNumber + ", (" + track.getAssetFile().getAbsolutePath() + ")", e);
			}
		}
		
		String compilation = tag.getFirst(FieldKey.IS_COMPILATION);
		if (compilation != null && (compilation.trim().equals("1") || compilation.trim().equalsIgnoreCase("yes") || compilation.trim().equalsIgnoreCase("true"))) {
			track.setCompilation(true);
		}
		
		Artwork artwork = tag.getFirstArtwork();
		track.setArtworkAvailable(artwork != null);
		
		return track;
	}
	
	@Override
	public BufferedImage extractArtwork(File assetFile) throws Exception {
		AudioFile audioFile = AudioFileIO.read(assetFile);
		Tag tag = audioFile.getTag();
		Artwork artwork = tag.getFirstArtwork();
		return artwork == null ? null : artwork.getImage();
	}
}
