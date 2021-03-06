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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.musicmount.builder.MusicMountBuildConfig;
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
	
	private ProgressHandler progressHandler = new LoggingProgressHandler(LOGGER, Level.FINE);

	private final MusicMountBuildConfig config;
	private final MusicMountServer server;
	private final LiveMountUpdater updater;

	public MusicMountLive() {
		this(new MusicMountBuildConfig());
	}
	
	public MusicMountLive(MusicMountBuildConfig config) {
		this(config, new MusicMountServerJetty(LOGGER_ACCESS_LOG));
	}
	
	public MusicMountLive(MusicMountBuildConfig config, MusicMountServer server) {
		this.config = config;
		this.server = server;
		this.updater = new LiveMountUpdater(60 * 1000L); // delay update for 60 seconds after change  
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

	private String getMusicPath() {
		return MUSIC_PATH;
	}
	
	private String getMountPath() {
		return MOUNT_PATH;
	}

	public String getHostName(String defaultName) {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return defaultName;
		}
	}
	
	public String getSitePath() {
		String path = getMountPath();
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (!path.endsWith("/")) {
			path = path + "/";
		}
		return path;
	}

	public URL getSiteURL(String hostName, int port) throws MalformedURLException {
		if (port == 80) {
			return new URL("http", hostName, getSitePath());
		} else {
			return new URL("http", hostName, port, getSitePath());
		}
	}

	public void start(FileResource musicFolder, int port, String user, String password) throws Exception {
		LOGGER.info("Starting Server...");
		LOGGER.info("Music folder: " + musicFolder.getPath());

		LiveMountBuilder mountBuilder = new LiveMountBuilder(config);
		LiveMountServlet servlet = new LiveMountServlet(mountBuilder.update(musicFolder, getMusicPath(), progressHandler));
		
		FolderContext music = new FolderContext(getMusicPath(), musicFolder.getPath().toFile());
		MountContext mount = new MountContext(getMountPath(), servlet);
		
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

		LiveMountBuilder updateBuilder = new LiveMountBuilder(config.clone(), mountBuilder.getRepository());
		updateBuilder.getConfig().setFull(false); // never do a full build when updating
		try {
			updater.start(musicFolder, getMusicPath(), updateBuilder, servlet);
			LOGGER.info("Auto updater is ready.");
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Auto updater could not be started", e);
		}
	}
	
	public void await() {
		server.await();
	}

	public boolean isStarted() {
		return server.isStarted();
	}

	public void stop() throws Exception {
		LOGGER.info("Stopping Server...");
		if (updater.isStarted()) {
			updater.stop();
		}
		server.stop();
		LOGGER.info("Done.");
	}


}
