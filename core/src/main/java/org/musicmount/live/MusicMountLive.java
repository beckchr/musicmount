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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.AssetParser;
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
import org.musicmount.server.MusicMountServer;
import org.musicmount.server.MusicMountServer.AccessLog;
import org.musicmount.server.MusicMountServer.FolderContext;
import org.musicmount.server.MusicMountServer.MountContext;
import org.musicmount.server.MusicMountServerJetty;
import org.musicmount.util.LoggingProgressHandler;
import org.musicmount.util.ProgressHandler;
import org.musicmount.util.VersionUtil;

public class MusicMountLive {
	protected static final Logger LOGGER = Logger.getLogger(MusicMountLive.class.getName());

	/**
	 * prevent ImageIO from using file cache
	 */
	static {
		ImageIO.setUseCache(false); // TODO not sure if this is really useful...
	}
	
	private static final String MOUNT_PATH = "/musicmount";
	private static final String MUSIC_PATH = "/music";

	/**
	 * API version string
	 */
	static final String API_VERSION = VersionUtil.getSpecificationVersion();	

	public static final AccessLog LOGGER_ACCESS_LOG = new AccessLog() {
		@Override
		public void log(AccessLog.Entry entry) {
			if (LOGGER.isLoggable(Level.FINE)) {
				StringBuilder builder = new StringBuilder();
				String uri = entry.getRequestURI();
				try {
					uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
				} catch (UnsupportedEncodingException e) {
					// should not happen
				}
				final int methodAndURIFormatLength = 39; // magic... log line length = 100
				int maxURILength = methodAndURIFormatLength - 1 - entry.getRequestMethod().length();
				if (uri.length() > maxURILength) {
					uri = "..." + uri.substring(uri.length() - maxURILength + 3);
				}
				String methodAndURI = String.format("%s %s", entry.getRequestMethod(), uri);
				builder.append(String.format(String.format("%%-%ds", methodAndURIFormatLength), methodAndURI));
				builder.append(String.format("%4d", entry.getResponseStatus()));
				String contentLengthHeader = entry.getResponseHeader("Content-Length");
				if (contentLengthHeader != null) {
					builder.append(String.format(Locale.ENGLISH, "%,11dB", Long.valueOf(contentLengthHeader)));
				} else {
					builder.append("            ");
				}
				builder.append(String.format(Locale.ENGLISH, "%,7dms", entry.getResponseTimestamp() - entry.getRequestTimestamp()));
				LOGGER.log(Level.FINE, builder.toString());
			}
		}
	};
	
	private boolean retina = false;
	private boolean grouping = false;
	private boolean unknownGenre = false;
	private boolean noTrackIndex = false;
	private boolean noVariousArtists = false;
	private boolean full = false;
	private boolean verbose = false;
	
	private final AssetParser assetParser;	
	private final MusicMountServer server;

	public MusicMountLive() {
		this(new MusicMountServerJetty(LOGGER_ACCESS_LOG), new SimpleAssetParser());
	}

	public MusicMountLive(MusicMountServer server, AssetParser assetParser) {
		this.server = server;
		this.assetParser = assetParser;
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

	public boolean isVerbose() {
		return verbose;
	}
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public String getHostName(String defaultName) {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return defaultName;
		}
	}

	public URL getSiteURL(String hostName, int port) throws MalformedURLException {
		String path = MOUNT_PATH;
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (!path.endsWith("/")) {
			path = path + "/";
		}
		if (port == 80) {
			return new URL("http", hostName, path);
		} else {
			return new URL("http", hostName, port, path);
		}
	}

	Library loadLibrary(FileResource musicFolder, Resource assetStoreFile) throws Exception {
		ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, verbose ? Level.FINER : Level.FINE);

		AssetStore assetStore = new AssetStore(API_VERSION, musicFolder);
		boolean assetStoreLoaded = false;
		if (!full && assetStoreFile != null && assetStoreFile.exists()) {
			if (progressHandler != null) {
				progressHandler.beginTask(-1, "Loading asset store...");
			}
			InputStream input = assetStoreFile.getInputStream();
			if (assetStoreFile.getName().endsWith(".gz")) {
				input = new GZIPInputStream(input);
			}
			try (InputStream assetStoreInput = new BufferedInputStream(input)) {
				assetStore.load(assetStoreInput);
				assetStoreLoaded = true;
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load asset store", e);
				assetStore = new AssetStore(API_VERSION, musicFolder);
			}
			if (progressHandler != null) {
				progressHandler.endTask();
			}
		}

		assetStore.update(assetParser, 1, progressHandler);

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
			if (progressHandler != null) {
				progressHandler.beginTask(-1, "Saving asset store...");
			}
			OutputStream output = assetStoreFile.getOutputStream();
			if (assetStoreFile.getName().endsWith(".gz")) {
				output = new GZIPOutputStream(output);
			}
			try (OutputStream assetStoreOutput = new BufferedOutputStream(output)) {
				assetStore.save(assetStoreOutput);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to save asset store", e);
				assetStoreFile.delete();
			}
			if (progressHandler != null) {
				progressHandler.endTask();
			}
		}
		LOGGER.fine("Done.");
		
		return library;
	}
	
	public void start(FileResource musicFolder, Resource assetStore, int port, String user, String password) throws Exception {
		LOGGER.info("Starting Build...");
		LOGGER.info("Music folder: " + musicFolder.getPath());

		Library library = loadLibrary(musicFolder, assetStore);

		FolderContext music = new FolderContext(MUSIC_PATH, musicFolder.getPath().toFile());
		ResponseFormatter<?> responseFormatter =
				new ResponseFormatter.JSON(API_VERSION, new LocalStrings(), false, unknownGenre, grouping, false);
		ImageFormatter imageFormatter = new ImageFormatter(assetParser, retina);
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, music.getPath(), null);
		LiveMount liveMount = new LiveMount(library, responseFormatter, imageFormatter, assetLocator, noTrackIndex);
		MountContext mount = new MountContext(MOUNT_PATH, new LiveMountServlet(liveMount));
		
		LOGGER.info("Starting Server...");
		server.start(music, mount, port, user, password);
		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: %s", getSiteURL(getHostName("<hostName>"), port)));
		if (user != null) {
			LOGGER.info(String.format("User: %s", user));
			LOGGER.info(String.format("Pass: %s", "<not logged>"));
		}
		LOGGER.info(String.format("--------------"));
		LOGGER.info("Done.");
	}
	
	public void await() {
		server.await();
	}

	public boolean isStarted() {
		return server.isStarted();
	}

	public void stop() throws Exception {
		LOGGER.info("Stopping Server...");
		server.stop();
		LOGGER.info("Done.");
	}


}
