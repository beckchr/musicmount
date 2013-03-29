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

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.AlbumArtist;
import org.musicmount.builder.model.Disc;
import org.musicmount.builder.model.Library;
import org.musicmount.builder.model.Track;
import org.musicmount.builder.model.TrackArtist;

public class LibraryParser {
	static final Logger LOGGER = Logger.getLogger(LibraryParser.class.getName());
	
	private static <T extends Comparable<T>> int compareNullLast(Comparable<T> o1, T o2) {
		if (o1 != o2) {
			if (o1 == null) {
				return +1;
			} else if (o2 == null) {
				return -1;
			} else {
				int result = o1.compareTo(o2);
				if (result != 0) {
					return result;
				}
			}
		}
		return 0;
	}
	
	private final AssetParser assetParser;
	
	public LibraryParser(AssetParser assetParser) {
		this.assetParser = assetParser;
	}
	
	FileFilter searchAudioFilter = new FileFilter() {
		public boolean accept(File file) {
			return file.isDirectory() || assetParser.isAssetFile(file);
		}
	};

	void sortTracks(Library library) {
		/**
		 * sort disc tracks by track number, title, artist, year, genre
		 */
		for (Album album : library.getAlbums()) {
			for (Disc disc : album.getDiscs().values()) {
				Collections.sort(disc.getTracks(), new Comparator<Track>() {
					@Override
					public int compare(Track o1, Track o2) {
						// 
						int result = compareNullLast(o1.getTrackNumber(), o2.getTrackNumber());
						if (result != 0) {
							return result;
						}
						result = compareNullLast(o1.getName(), o2.getName());
						if (result != 0) {
							return result;
						}
						result = compareNullLast(o1.getArtist(), o2.getArtist());
						if (result != 0) {
							return result;
						}
						result = compareNullLast(o1.getYear(), o2.getYear());
						if (result != 0) {
							return result;
						}
						result = compareNullLast(o1.getGenre(), o2.getGenre());
						if (result != 0) {
							return result;
						}
						return 0;
					}
				});
			}
		}

		/**
		 * sort album tracks by disc number, track number, title, artist, year, genre
		 */
		for (Album album : library.getAlbums()) {
			Collections.sort(album.getTracks(), new Comparator<Track>() {
				@Override
				public int compare(Track o1, Track o2) {
					int result = compareNullLast(o1.getDiscNumber(), o2.getDiscNumber());
					if (result != 0) {
						return result;
					}
					result = compareNullLast(o1.getTrackNumber(), o2.getTrackNumber());
					if (result != 0) {
						return result;
					}
					result = compareNullLast(o1.getName(), o2.getName());
					if (result != 0) {
						return result;
					}
					result = compareNullLast(o1.getArtist(), o2.getArtist());
					if (result != 0) {
						return result;
					}
					result = compareNullLast(o1.getYear(), o2.getYear());
					if (result != 0) {
						return result;
					}
					result = compareNullLast(o1.getGenre(), o2.getGenre());
					if (result != 0) {
						return result;
					}
					return 0;
				}
			});
		}
	}

	private String uniqueArtistName(Album album) {
		String artist = album.getTracks().get(0).getArtist();
		if (artist != null) {
			for (Track track : album.getTracks()) {
				if (!artist.equals(track.getArtist())) {
					return null;
				}
			}
		}
		return artist;
	}

	public final Library parse(File assetBaseDir, TrackStore trackStore) {
		Library library = new Library();
		
		// add "various artists" with id 0
		library.getAlbumArtists().put(null, new AlbumArtist(0, null));
		
		// add "unknown artist" with id 0
		library.getTrackArtists().put(null, new TrackArtist(0, null));

		// parse tracks into library
		parseLibrary(library, assetBaseDir.getAbsoluteFile(), trackStore);

		/*
		 * distribute compilations without album artist into "various artists" and
		 * "unique artist" albums (which are moved to the corresponding album artist). 
		 */
		AlbumArtist variousArtists = library.getAlbumArtists().get(null);
		Iterator<Album> variousArtistsAlbumIterator = variousArtists.getAlbums().values().iterator();
		while (variousArtistsAlbumIterator.hasNext()) {
			Album album = variousArtistsAlbumIterator.next();
			String uniqueArtistName = uniqueArtistName(album);
			if (uniqueArtistName != null) {
				// insert album to unique artist
				AlbumArtist albumArtist = library.getAlbumArtists().get(uniqueArtistName);
				if (albumArtist == null) {
					albumArtist = new AlbumArtist(library.getAlbumArtists().size(), uniqueArtistName);
					library.getAlbumArtists().put(uniqueArtistName, albumArtist);
				}
				// move album from variousArtists to albumArtist
				albumArtist.getAlbums().put(album.getTitle(), album);
				album.setAlbumArtist(albumArtist);
				variousArtistsAlbumIterator.remove();
			}
		}

		// remove empty "various artists"
		if (library.getAlbumArtists().get(null).getAlbums().isEmpty()) {
			library.getAlbumArtists().remove(null);
		}
		
		// remove empty "unknown artist"
		if (library.getTrackArtists().get(null).getAlbums().isEmpty()) {
			library.getTrackArtists().remove(null);
		}
		
		// sort tracks
		sortTracks(library);

		return library;
	}

	void parseLibrary(Library library, File directory, TrackStore trackStore) {
		for (File file : directory.listFiles(searchAudioFilter)) {
			if (file.isDirectory()) {
				parseLibrary(library, file, trackStore);
			} else {
				Track track = null;
				try {
					track = trackStore.getTrack(file);
					if (track == null) {
						track = assetParser.parse(file);
					}
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Could not parse audio file: " + file.getAbsolutePath(), e);
					continue;
				}
				if (track.getName() == null || track.getAlbum() == null && track.getArtist() == null) { // unusable
					LOGGER.log(Level.INFO, "Will skip poorly tagged audio file: " + file.getAbsolutePath());
					continue;
				}
				
				/*
				 * determine album artist
				 */
				String albumArtistName = track.getAlbumArtist();
				if (albumArtistName == null && !track.isCompilation()) {
					albumArtistName = track.getArtist();
				}
				AlbumArtist albumArtist = library.getAlbumArtists().get(albumArtistName);
				if (albumArtist == null) {
					albumArtist = new AlbumArtist(library.getAlbumArtists().size(), albumArtistName);
					library.getAlbumArtists().put(albumArtistName, albumArtist);
				}
				
				/*
				 * determine album
				 */
				Album album = albumArtist.getAlbums().get(track.getAlbum());
				if (album == null) {
					album = trackStore.createAlbum(track, track.getAlbum());
					album.setAlbumArtist(albumArtist);
					albumArtist.getAlbums().put(track.getAlbum(), album);
					library.getAlbums().add(album);
					if (library.getAlbums().size() % 100 == 0) {
						LOGGER.fine("Progress: #albums = " + library.getAlbums().size());
					}
				} else {
					trackStore.addTrack(track, album);
				}
				album.getTracks().add(track);

				/*
				 * determine disc
				 */
				Integer discKey = track.getDiscNumber();
				if (discKey == null) {
					discKey = Integer.valueOf(0);
				}
				Disc disc = album.getDiscs().get(discKey);
				if (disc == null) {
					disc = new Disc(discKey.intValue());
					album.getDiscs().put(discKey, disc);
				}
				disc.getTracks().add(track);
				
				/*
				 * determine track artist
				 */
				TrackArtist trackArtist = library.getTrackArtists().get(track.getArtist());
				if (trackArtist == null) {
					trackArtist = new TrackArtist(library.getTrackArtists().size(), track.getArtist());
					library.getTrackArtists().put(track.getArtist(), trackArtist);
				}
				trackArtist.getAlbums().add(album);
				
				/*
				 * make sure the album artist also appears as track artist???
				 */
//				if (albumArtistName != null && !albumArtistName.equals(track.getArtist())) {
//					TrackArtist albumTrackArtist = library.getTrackArtists().get(albumArtistName);
//					if (albumTrackArtist == null) {
//						albumTrackArtist = new TrackArtist(library.getTrackArtists().size(), albumArtistName);
//						library.getTrackArtists().put(albumArtistName, albumTrackArtist);
//					}
//					albumTrackArtist.getTracks().add(track);
//					albumTrackArtist.getAlbums().add(album);
//				}
			}
		}
	}
}
