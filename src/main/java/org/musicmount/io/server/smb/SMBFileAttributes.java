package org.musicmount.io.server.smb;

import java.nio.file.attribute.FileTime;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import org.musicmount.io.server.ServerFileAttributes;

public class SMBFileAttributes implements ServerFileAttributes {
	private final SmbFile file;

	public SMBFileAttributes(SmbFile file) {
		this.file = file;
	}

	@Override
	public FileTime lastModifiedTime() {
		return FileTime.fromMillis(file.getLastModified());
	}

	@Override
	public FileTime lastAccessTime() {
		return null;
	}

	@Override
	public FileTime creationTime() {
		return null;
	}

	@Override
	public boolean isRegularFile() {
		try {
			return file.isFile();
		} catch (SmbException e) {
			return false;
		}
	}

	@Override
	public boolean isDirectory() {
		try {
			return file.isDirectory();
		} catch (SmbException e) {
			return false;
		}
	}

	@Override
	public boolean isSymbolicLink() {
		return false;
	}

	@Override
	public boolean isOther() {
		return false;
	}

	@Override
	public long size() {
		try {
			return file.length();
		} catch (SmbException e) {
			return -1L;
		}
	}

	@Override
	public Object fileKey() {
		return null;
	}

	@Override
	public String getPath() {
		return file.getURL().getPath();
	}
}
