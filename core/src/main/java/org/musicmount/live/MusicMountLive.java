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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.builder.MusicMountBuilder;
import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.AssetParser;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.LocalStrings;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.impl.SimpleAssetLocator;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.model.Library;
import org.musicmount.io.file.FileResource;
import org.musicmount.server.MusicMountServer;
import org.musicmount.server.MusicMountServer.AccessLog;
import org.musicmount.server.MusicMountServer.FolderContext;
import org.musicmount.server.MusicMountServerJetty;
import org.musicmount.util.LoggingProgressHandler;
import org.musicmount.util.ProgressHandler;

public class MusicMountLive {
	protected static final Logger LOGGER = Logger.getLogger(MusicMountLive.class.getName());

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
	private boolean verbose = false;
	
	private final MusicMountServer server;

	public MusicMountLive() {
		this(new MusicMountServerJetty(LOGGER_ACCESS_LOG));
	}

	public MusicMountLive(MusicMountServer server) {
		this.server = server;
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

	public boolean isVerbose() {
		return verbose;
	}
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public String getSiteURL(int port) {
		String host = null;
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			host = "<hostname>";
		}
		return String.format("http://%s:%d/musicmount/", host, port);
	}

	public void start(FileResource musicFolder, int port, String user, String password) throws Exception {
		AssetStore assetStore = new AssetStore(MusicMountBuilder.API_VERSION);
		AssetParser assetParser = new SimpleAssetParser();
		ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, verbose ? Level.FINER : Level.FINE);
		assetStore.update(musicFolder, assetParser, 1, progressHandler);
		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Building music libary...");
		}
		Library library = new LibraryParser(grouping).parse(assetStore.assets());
		if (noVariousArtists) { // remove "various artists" album artist (hack)
			library.getAlbumArtists().remove(null);
		}
		assetStore.sync(library.getAlbums());
		if (progressHandler != null) {
			progressHandler.endTask();
		}

		ResponseFormatter<?> responseFormatter = new ResponseFormatter.JSON(MusicMountBuilder.API_VERSION, new LocalStrings(), false, unknownGenre, grouping, false);
		ImageFormatter imageFormatter = new ImageFormatter(assetParser, retina);
		FolderContext music = new FolderContext("/music", musicFolder.getPath().toFile());
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, music.getPath(), null);
		LiveContext context = new LiveContext(music, "/musicmount", library, responseFormatter, imageFormatter, assetLocator, noTrackIndex);
		
		LOGGER.info("Starting Server...");
		server.start(context, port, user, password);
		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: %s", getSiteURL(port)));
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
