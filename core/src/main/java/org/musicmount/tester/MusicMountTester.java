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
package org.musicmount.tester;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.file.FileResource;
import org.musicmount.server.MusicMountServer;
import org.musicmount.server.MusicMountServerJetty;
import org.musicmount.server.MusicMountServer.AccessLog;
import org.musicmount.server.MusicMountServer.FolderContext;

public class MusicMountTester {
	protected static final Logger LOGGER = Logger.getLogger(MusicMountTester.class.getName());
	
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
	
	private final MusicMountServer engine;
	
	public MusicMountTester() {
		this(new MusicMountServerJetty(LOGGER_ACCESS_LOG));
	}
	
	public MusicMountTester(MusicMountServer engine) {
		this.engine = engine;
	}

	public void start(
			FileResource musicFolder,
			FileResource mountFolder,
			String musicPath,
			int port,
			final String user,
			final String password) throws Exception {
		if (!checkMusicPath(musicPath)) {
			throw new IllegalArgumentException("Unsupported music path");
		}
		FolderContext musicContext = new FolderContext(musicContextPath(musicPath), musicFolder.getPath().toFile());
		FolderContext mountContext = new FolderContext(mountContextPath(musicPath), mountFolder.getPath().toFile());

		LOGGER.info("Starting Server...");
		LOGGER.info("Music folder: " + musicFolder.getPath());
		LOGGER.info("Mount folder: " + mountFolder.getPath());
		LOGGER.info("Music path  : " + musicPath);
		engine.start(musicContext, mountContext, port, user, password);
		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: %s", getSiteURL(getHostName("<hostName>"), port, musicPath)));
		if (user != null) {
			LOGGER.info(String.format("User: %s", user));
			LOGGER.info(String.format("Pass: %s", "<not logged>"));
		}
		LOGGER.info(String.format("--------------"));
		LOGGER.info("Done.");
	}

	public void await() {
		engine.await();
	}

	public boolean isStarted() {
		return engine.isStarted();
	}

	public void stop() throws Exception {
		LOGGER.info("Stopping Server...");
		engine.stop();
		LOGGER.info("Done.");
	}

	private int upLevels(String[] segments) {
		int upLevels = 0;
		while (segments[upLevels].equals("..")) {
			if (++upLevels == segments.length) {
				break;
			}
		}
		return upLevels;
	}
	
	String normalizeMusicPath(String musicPath) {
		musicPath = musicPath.trim();
		musicPath = musicPath.replace(FileSystems.getDefault().getSeparator(), "/");
		musicPath = musicPath.replaceAll("/+", "/");
		musicPath = musicPath.replaceAll("/\\./", "/");
		musicPath = musicPath.replaceAll("^\\./|/\\.$", "");
		if (musicPath.endsWith("/")) {
			musicPath = musicPath.substring(0, musicPath.length() - 1);
		}
		return musicPath;
	}
	
	public boolean checkMusicPath(String musicPath) {
		if (musicPath == null || musicPath.trim().isEmpty()) {
			return false;
		}
		musicPath = normalizeMusicPath(musicPath);
		String[] segments = musicPath.split("/");
		int downStartIndex = upLevels(segments);
		if (downStartIndex == segments.length) {
			return false;
		}
		for (int i = downStartIndex; i < segments.length; i++) {
			if (segments[i].equals("..")) {
				return false;
			}
		}
		return true;
	}

	protected String musicContextPath(String musicPath) {
		musicPath = normalizeMusicPath(musicPath);
		if (musicPath.startsWith("../")) { // up-down-path, e.g. "../../../music" -> "/music"
			return musicPath.substring(3 * upLevels(musicPath.split("/")) - 1);
		} else if (musicPath.startsWith("/")) { // absolute path, e.g. "/music" -> "/music"
			return musicPath;
		} else { // down-path, e.g. "music"
			return "/musicmount/" + musicPath;
		}
	}

	protected String mountContextPath(String musicPath) {
		musicPath = normalizeMusicPath(musicPath);
		int upLevels = upLevels(musicPath.split("/"));
		StringBuilder builder = new StringBuilder("/musicmount");
		for (int i = 1; i < upLevels; i++) {
			builder.append("/").append(i + 1);
		}
		return builder.toString();
	}
	
	public String getHostName(String defaultName) {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return defaultName;
		}
	}

	public String getSitePath(String musicPath) {
		if (!checkMusicPath(musicPath)) {
			return null;
		}
		return String.format("%s/index.json", mountContextPath(musicPath));
	}
	
	public URL getSiteURL(String hostName, int port, String musicPath) throws MalformedURLException {
		if (port == 80) {
			return new URL("http", hostName, getSitePath(musicPath));
		} else {
			return new URL("http", hostName, port, getSitePath(musicPath));
		}
	}
}
