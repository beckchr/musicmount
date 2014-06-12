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
package org.musicmount.builder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.LocalStrings;
import org.musicmount.builder.impl.ResourceLocator;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.impl.SimpleAssetLocator;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.impl.SimpleResourceLocator;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Library;
import org.musicmount.io.Resource;
import org.musicmount.util.LoggingProgressHandler;
import org.musicmount.util.ProgressHandler;
import org.musicmount.util.VersionUtil;

public class MusicMountBuilder {
	static final Logger LOGGER = Logger.getLogger(MusicMountBuilder.class.getName());

	/**
	 * prevent ImageIO from using file cache
	 */
	static {
		ImageIO.setUseCache(false); // TODO not sure if this is really useful...
	}

	/**
	 * API version string
	 */
	static final String API_VERSION = VersionUtil.getSpecificationVersion();	

	/**
	 * Name of asset store file.
	 */
	static final String ASSET_STORE = ".musicmount.gz";	

	private boolean retina = false;
	private boolean pretty = false;
	private boolean full = false;
	private boolean noImages = false;
	private boolean xml = false;
	private boolean grouping = false;
	private boolean unknownGenre = false;
	private boolean noTrackIndex = false;
	private boolean noVariousArtists = false;
	private boolean directoryIndex = false;
	private Normalizer.Form normalizer = null;

	private ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, Level.FINE);

	private final int maxAssetThreads;
	private final int maxImageThreads;

	public MusicMountBuilder() {
		this(1, Integer.MAX_VALUE);
	}

	public MusicMountBuilder(int maxAssetThreads, int maxImageThreads) {
		this.maxAssetThreads = maxAssetThreads;
		this.maxImageThreads = maxImageThreads;
	}

	public ProgressHandler getProgressHandler() {
		return progressHandler;
	}
	public void setProgressHandler(ProgressHandler progressHandler) {
		this.progressHandler = progressHandler;
	}

	public boolean isRetina() {
		return retina;
	}
	public void setRetina(boolean retina) {
		this.retina = retina;
	}

	public boolean isPretty() {
		return pretty;
	}
	public void setPretty(boolean pretty) {
		this.pretty = pretty;
	}

	public boolean isFull() {
		return full;
	}
	public void setFull(boolean full) {
		this.full = full;
	}

	public boolean isNoImages() {
		return noImages;
	}
	public void setNoImages(boolean noImages) {
		this.noImages = noImages;
	}

	public boolean isXml() {
		return xml;
	}
	public void setXml(boolean xml) {
		this.xml = xml;
	}

	public boolean isGrouping() {
		return grouping;
	}
	public void setGrouping(boolean grouping) {
		this.grouping = grouping;
	}

	public boolean isUnknownGenre() {
		return unknownGenre;
	}

	public void setUnknownGenre(boolean unknownGenre) {
		this.unknownGenre = unknownGenre;
	}
	
	public boolean isNoTrackIndex() {
		return noTrackIndex;
	}
	public void setNoTrackIndex(boolean noTrackIndex) {
		this.noTrackIndex = noTrackIndex;
	}

	public boolean isNoVariousArtists() {
		return noVariousArtists;
	}
	public void setNoVariousArtists(boolean noVariousArtists) {
		this.noVariousArtists = noVariousArtists;
	}

	public boolean isDirectoryIndex() {
		return directoryIndex;
	}
	public void setDirectoryIndex(boolean directoryIndex) {
		this.directoryIndex = directoryIndex;
	}

	public Normalizer.Form getNormalizer() {
		return normalizer;
	}
	public void setNormalizer(Normalizer.Form normalizer) {
		this.normalizer = normalizer;
	}

	private OutputStream createOutputStream(Resource file) throws IOException {
		if (!file.getParent().exists()) { // file.getParent() may be a symbolic link target (mount folder)
			file.getParent().mkdirs();
		}
		return new BufferedOutputStream(file.getOutputStream());
	}

	public void build(Resource musicFolder, Resource mountFolder, String musicPath) throws Exception {
		Resource assetStoreFile = mountFolder.resolve(ASSET_STORE);

		LOGGER.info("Starting Build...");
		LOGGER.info("Music folder: " + musicFolder.getPath());
		LOGGER.info("Mount folder: " + mountFolder.getPath());
		LOGGER.info("Music path  : " + musicPath);

		AssetStore assetStore = new AssetStore(API_VERSION, musicFolder);
		boolean assetStoreLoaded = false;
		if (!full) {
			try {
				if (assetStoreFile.exists()) {
					assetStore.load(assetStoreFile, progressHandler);
					assetStoreLoaded = true;
				}
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load asset store", e);
				assetStore = new AssetStore(API_VERSION, musicFolder);
			}
		}

		assetStore.update(new SimpleAssetParser(), maxAssetThreads, progressHandler);

		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Building music libary...");
		}
		Library library = new LibraryParser(grouping).parse(assetStore.assets());
		if (noVariousArtists) { // remove "various artists" album artist (hack)
			library.getAlbumArtists().remove(null);
		}
		Set<Album> changedAlbums = assetStore.sync(library.getAlbums());
		if (assetStoreLoaded) {
			LOGGER.fine(String.format("Number of albums changed: %d", changedAlbums.size()));
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}

		if (noImages) {
			assetStore.setRetina(null);
		} else {
			ImageFormatter formatter = new ImageFormatter(new SimpleAssetParser(), retina);
			final boolean retinaChange = !Boolean.valueOf(retina).equals(assetStore.getRetina());
			if (LOGGER.isLoggable(Level.FINE) && retinaChange && assetStoreLoaded) {
				LOGGER.fine(String.format("Retina state %s", assetStore.getRetina() == null ? "unknown" : "changed"));
			}
			ResourceLocator resourceLocator = new SimpleResourceLocator(mountFolder, xml, noImages, noTrackIndex);
			Set<Album> imageAlbums = retinaChange || full ? new HashSet<>(library.getAlbums()) : changedAlbums;
			formatter.formatImages(library, resourceLocator, imageAlbums, maxImageThreads, progressHandler);
			assetStore.setRetina(retina);
		}
		
		generateResponseFiles(library, musicFolder, mountFolder, musicPath);

		if (!assetStoreLoaded || changedAlbums.size() > 0) {
			try {
				assetStore.save(assetStoreFile, progressHandler);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to save asset store", e);
			}
		}

		LOGGER.info("Done.");
	}

	void generateResponseFiles(Library library, Resource musicFolder, Resource mountFolder, String musicPath) throws Exception {
		if (progressHandler != null) {
			progressHandler.beginTask(noTrackIndex ? 3 : 4, "Generating JSON...");
		}

		LocalStrings localStrings = new LocalStrings(Locale.ENGLISH);
		ResponseFormatter<?> formatter;
		if (xml) {
			formatter = new ResponseFormatter.XML(API_VERSION, localStrings, directoryIndex, unknownGenre, grouping, pretty);
		} else {
			formatter = new ResponseFormatter.JSON(API_VERSION, localStrings, directoryIndex, unknownGenre, grouping, pretty);
		}
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, musicPath, normalizer);
		ResourceLocator resourceLocator = new SimpleResourceLocator(mountFolder, xml, noImages, noTrackIndex);

		int workDone = -1;
		
		/*
		 * album artists
		 */
		Map<Artist, Album> representativeAlbums = new HashMap<Artist, Album>();
		for (Artist artist : library.getAlbumArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for album artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist)))) {
			formatter.formatArtistIndex(library.getAlbumArtists().values(), ArtistType.AlbumArtist, output, resourceLocator, representativeAlbums);
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%5d album artists", library.getAlbumArtists().size()));
		}

		/*
		 * artists
		 */
		representativeAlbums.clear();
		for (Artist artist : library.getTrackArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getArtistIndexPath(ArtistType.TrackArtist)))) {
			formatter.formatArtistIndex(library.getTrackArtists().values(), ArtistType.TrackArtist, output, resourceLocator, representativeAlbums);
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%5d artists", library.getTrackArtists().size()));
		}

		/*
		 * albums
		 */
		for (Album album : library.getAlbums()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album: " + album.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumPath(album)))) {
				formatter.formatAlbum(album, output, resourceLocator, assetLocator);
			}
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumIndexPath()))) {
			formatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%5d albums", library.getAlbums().size()));
		}
		
		/*
		 * track index
		 */
		if (!noTrackIndex) {
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getTrackIndexPath()))) {
				formatter.formatTrackIndex(library.getTracks(), output, resourceLocator, null);
			}
			if (progressHandler != null) {
				progressHandler.progress(++workDone, String.format("%5d tracks", library.getTracks().size()));
			}
		}

		/*
		 * service index last
		 */
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getServiceIndexPath()))) {
			formatter.formatServiceIndex(resourceLocator, output);
		}

		if (progressHandler != null) {
			progressHandler.endTask();
		}
	}
}
