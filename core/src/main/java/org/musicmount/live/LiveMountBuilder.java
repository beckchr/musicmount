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

import org.musicmount.builder.MusicMountBuildConfig;
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

	static FileResource getRepository() {
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			FileResource repo = new FileResourceProvider(userHome).getBaseDirectory().resolve(".musicmount");
			try {
				if (!repo.exists()) {
					repo.mkdirs();
				}
				return repo;
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Could not access user repository", e);
			}
		} else {
			LOGGER.info("System property 'user.home' is not set");
		}
		LOGGER.info("Creating temporary repository folder");
		String tempFolder = null;
		try {
			tempFolder = Files.createTempDirectory("musicmount-").toFile().getAbsolutePath();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not create temporary repository folder", e);
		}
		if (tempFolder != null) {
			return new FileResourceProvider(tempFolder).getBaseDirectory();
		}
		return null;
	}
	
	private final Resource repository;
	private final MusicMountBuildConfig config;

	private ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, Level.FINE);

	public LiveMountBuilder() {
		this(new MusicMountBuildConfig());
	}
	
	public LiveMountBuilder(MusicMountBuildConfig config) {
		this(config, getRepository());
	}

	public LiveMountBuilder(MusicMountBuildConfig config, Resource repository) {
		this.config = config;
		this.repository = repository;
	}
	
	public MusicMountBuildConfig getConfig() {
		return config;
	}
	
	public ProgressHandler getProgressHandler() {
		return progressHandler;
	}
	public void setProgressHandler(ProgressHandler progressHandler) {
		this.progressHandler = progressHandler;
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
		if (!config.isFull() && assetStoreFile != null) {
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
		Library library = new LibraryParser(config.isGrouping()).parse(assetStore.assets());
		if (config.isNoVariousArtists()) { // remove "various artists" album artist (hack)
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
				if (progressHandler != null) {
					progressHandler.endTask();
				}
			}
		}
		
		ResponseFormatter<?> responseFormatter;
		if (config.isXml()) {
			responseFormatter = new ResponseFormatter.XML(API_VERSION, new LocalStrings(), config.isDirectoryIndex(), config.isUnknownGenre(), config.isGrouping(), config.isPretty());
		} else {
			responseFormatter = new ResponseFormatter.JSON(API_VERSION, new LocalStrings(), config.isDirectoryIndex(), config.isUnknownGenre(), config.isGrouping(), config.isPretty());
		}
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, musicPath, config.getNormalizer());
		ImageFormatter imageFormatter = new ImageFormatter(new SimpleAssetParser(), config.isRetina());
		return new LiveMount(library, responseFormatter, imageFormatter, assetLocator, config.isNoTrackIndex());
	}
}
