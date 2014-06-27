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
package org.musicmount.fx;

import java.nio.file.FileSystems;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.musicmount.builder.MusicMountBuildConfig;
import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;

public class FXCommandModel {
	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FXCommandModel.class);
	private static final String PREFERENCE_KEY_GROUPING = "builder.grouping";
	private static final String PREFERENCE_KEY_NO_TRACK_INDEX = "builder.noTrackIndex";
	private static final String PREFERENCE_KEY_NO_VARIOUS_ARTISTS = "builder.noVariousArtists";
	private static final String PREFERENCE_KEY_RETINA = "builder.retina";
	private static final String PREFERENCE_KEY_UNKNOWN_GENRE = "builder.unknownGenre";
	private static final String PREFERENCE_KEY_PORT = "server.port";
	private static final String PREFERENCE_KEY_BONJOUR = "server.bonjour";

	public static final String DEFAULT_CUSTOM_MUSIC_PATH = "music";

	private final FileResourceProvider fileResourceProvider = new FileResourceProvider();
	private final MusicMountBuildConfig buildConfig = new MusicMountBuildConfig();

	private FileResource musicFolder;
	private FileResource mountFolder;
	private String customMusicPath;
	private Integer serverPort;
	private boolean bonjour;

	public FXCommandModel() {
		loadPreferences();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					savePreferences();
				} catch (BackingStoreException e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public MusicMountBuildConfig getBuildConfig() {
		return buildConfig;
	}
	
	private void loadPreferences() {
		getBuildConfig().setGrouping(PREFERENCES.getBoolean(PREFERENCE_KEY_GROUPING, false));
		getBuildConfig().setNoTrackIndex(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, false));
		getBuildConfig().setNoVariousArtists(PREFERENCES.getBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, false));
		getBuildConfig().setRetina(PREFERENCES.getBoolean(PREFERENCE_KEY_RETINA, false));
		getBuildConfig().setUnknownGenre(PREFERENCES.getBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, false));
		serverPort = Integer.valueOf(PREFERENCES.getInt(PREFERENCE_KEY_PORT, 8080));
		if (serverPort == 0) {
			serverPort = null;
		}
		bonjour = PREFERENCES.getBoolean(PREFERENCE_KEY_BONJOUR, false);
	}

	private void savePreferences() throws BackingStoreException {
		PREFERENCES.putBoolean(PREFERENCE_KEY_GROUPING, getBuildConfig().isGrouping());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_TRACK_INDEX, getBuildConfig().isNoTrackIndex());
		PREFERENCES.putBoolean(PREFERENCE_KEY_NO_VARIOUS_ARTISTS, getBuildConfig().isNoVariousArtists());
		PREFERENCES.putBoolean(PREFERENCE_KEY_RETINA, getBuildConfig().isRetina());
		PREFERENCES.putBoolean(PREFERENCE_KEY_UNKNOWN_GENRE, getBuildConfig().isUnknownGenre());
		if (serverPort != null) {
			PREFERENCES.putInt(PREFERENCE_KEY_PORT, serverPort.intValue());
		} else {
			PREFERENCES.remove(PREFERENCE_KEY_PORT);
		}
		PREFERENCES.putBoolean(PREFERENCE_KEY_BONJOUR, bonjour);
		PREFERENCES.flush();
	}
	
	public FileResource getMusicFolder() {
		return musicFolder;
	}
	public void setMusicFolder(FileResource musicFolder) {
		this.musicFolder = musicFolder;
	}

	public FileResource getMountFolder() {
		return mountFolder;
	}
	public void setMountFolder(FileResource mountFolder) {
		this.mountFolder = mountFolder;
	}

	public String getCustomMusicPath() {
		return customMusicPath;
	}
	public void setCustomMusicPath(String musicPath) {
		this.customMusicPath = musicPath;
	}
	
	public Integer getServerPort() {
		return serverPort;
	}
	public void setServerPort(Integer serverPort) {
		this.serverPort = serverPort;
	}
	
	public boolean isBonjour() {
		return bonjour;
	}
	public void setBonjour(boolean bonjour) {
		this.bonjour = bonjour;
	}
	
	public FileResource toFolder(String path) {
		if (path != null) {
			FileResource folder = fileResourceProvider.newResource(path);
			if (folder.isDirectory()) {
				return folder;
			}
		}
		return null;
	}
	
	public String getMusicPath() {
		if (customMusicPath != null) {
			return customMusicPath.replace(FileSystems.getDefault().getSeparator(), "/");
		}
		if (musicFolder == null || mountFolder == null) {
			return null;
		}
		String relativePath = null;
		try { // calculate relative path from mountFolder to musicFolder
			relativePath = mountFolder.getPath().relativize(musicFolder.getPath()).toString();
		} catch (IllegalArgumentException e) {
			return null;
		}
		return relativePath.replace(FileSystems.getDefault().getSeparator(), "/");
	}

	boolean isValidLiveModel() {
		return musicFolder != null && serverPort != null;
	}

	boolean isValidBuildModel() {
		if (musicFolder == null ||  mountFolder == null) {
			return false;
		}
		FileResource folder = mountFolder;
		while (folder != null) {
			if (folder.equals(musicFolder)) {
				return false;
			}
			folder = folder.getParent();
		}
		String musicPath = getMusicPath();
		return musicPath != null && !musicPath.isEmpty();
	}

	boolean isValidSiteModel() {
		return isValidBuildModel() && serverPort != null &&  mountFolder.resolve("index.json").exists();
	}
}
