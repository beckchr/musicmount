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
package org.musicmount.io.server.dav;

import java.nio.file.attribute.FileTime;

import org.musicmount.io.server.ServerFileAttributes;

import com.github.sardine.DavResource;

public class DAVFileAttributes implements ServerFileAttributes {
	private final DavResource resource;

	DAVFileAttributes(DavResource res) {
		this.resource = res;
	}
	
	@Override
	public String getPath() {
		return resource.getPath();
	}

	@Override
	public long size() {
		return resource.getContentLength() != null ? resource.getContentLength() : -1;
	}

	@Override
	public FileTime lastModifiedTime() {
		return FileTime.fromMillis(resource.getModified().getTime());
	}

	@Override
	public FileTime lastAccessTime() {
		return FileTime.fromMillis(System.currentTimeMillis());
	}

	@Override
	public boolean isSymbolicLink() {
		return false;
	}

	@Override
	public boolean isRegularFile() {
		return !resource.isDirectory();
	}

	@Override
	public boolean isOther() {
		return false;
	}

	@Override
	public boolean isDirectory() {
		return resource.isDirectory();
	}

	@Override
	public Object fileKey() {
		return null;
	}

	@Override
	public FileTime creationTime() {
		return FileTime.fromMillis(resource.getCreation().getTime());
	}
}