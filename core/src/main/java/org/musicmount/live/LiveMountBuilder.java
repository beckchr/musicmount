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
package org.musicmount.live;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.LocalStrings;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.impl.SimpleAssetLocator;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Library;
import org.musicmount.io.Resource;
import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.LoggingProgressHandler;
import org.musicmount.util.ProgressHandler;
import org.musicmount.util.VersionUtil;

public class LiveMountBuilder {
	static final Logger LOGGER = Logger.getLogger(LiveMountBuilder.class.getName());

	/**
	 * API version string
	 */
	static final String API_VERSION = VersionUtil.getSpecificationVersion();	

	private final Resource repository;
	
	private boolean retina = false;
	private boolean grouping = false;
	private boolean unknownGenre = false;
	private boolean noTrackIndex = false;
	private boolean noVariousArtists = false;
	private boolean full = false;

	private ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, Level.FINE);

	public LiveMountBuilder() {
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			repository = new FileResourceProvider(userHome).getBaseDirectory().resolve(".musicmount");
		} else {
			LOGGER.info("System property 'user.home' is not set, creating temporary repository folder");
			String tempFolder = null;
			try {
				tempFolder = Files.createTempDirectory("musicmount-").toFile().getAbsolutePath();
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Could not create temporary repository folder", e);
			}
			if (tempFolder != null) {
				repository = new FileResourceProvider(tempFolder).getBaseDirectory();
			} else {
				repository = null;
			}
		}
	}

	public LiveMountBuilder(Resource repository) {
		this.repository = repository;
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
	
	public boolean isFull() {
		return full;
	}
	public void setFull(boolean full) {
		this.full = full;
	}

	private Resource assetStoreResource(FileResource musicFolder) {
		if (repository == null) {
			return null;
		}
		return repository.resolve(String.format("live-%08x.gz", musicFolder.getPath().toUri().toString().hashCode()));
	}
	
	public LiveMount update(FileResource musicFolder, String musicPath) throws IOException {
		Resource assetStoreFile = assetStoreResource(musicFolder);
		
		AssetStore assetStore = new AssetStore(API_VERSION, musicFolder);
		boolean assetStoreLoaded = false;
		if (!full && assetStoreFile != null) {
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

		assetStore.update(new SimpleAssetParser(), 1, progressHandler); // throws IOException

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

		if (assetStoreFile != null && changedAlbums.size() > 0) {
			try {
				assetStore.save(assetStoreFile, progressHandler);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to save asset store", e);
			}
		}
		
		ResponseFormatter<?> responseFormatter =
				new ResponseFormatter.JSON(API_VERSION, new LocalStrings(), false, unknownGenre, grouping, false);
		ImageFormatter imageFormatter = new ImageFormatter(new SimpleAssetParser(), retina);
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, musicPath, null);
		return new LiveMount(library, responseFormatter, imageFormatter, assetLocator, noTrackIndex);
	}
}
