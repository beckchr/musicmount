package org.musicmount.fx;

import java.nio.file.FileSystems;

import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;

public class FXCommandModel {
	public static final String DEFAULT_CUSTOM_MUSIC_PATH = "music";
	private final FileResourceProvider fileResourceProvider = new FileResourceProvider();

	private FileResource musicFolder;
	private FileResource mountFolder;
	private String customMusicPath;

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
	
	boolean isValid() {
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

	boolean isSite() {
		return isValid() && mountFolder.resolve("index.json").exists();
	}
}