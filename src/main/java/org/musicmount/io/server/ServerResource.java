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
package org.musicmount.io.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.musicmount.io.Resource;

public class ServerResource implements Resource {
	private final ServerResourceProvider provider;

	private ServerPath path;
	private BasicFileAttributes attributes;

	ServerResource(ServerResourceProvider provider, ServerPath path) {
		this.provider = provider;
		this.path = path;
	}
	
	ServerResource(ServerResourceProvider provider, ServerPath path, BasicFileAttributes attributes) {
		this(provider, path);
		this.attributes = attributes;
	}
	
	BasicFileAttributes getAttributes() throws IOException {
		if (attributes == null) {
			attributes = provider.getFileAttributes(path);
			if (path.isDirectory() != attributes.isDirectory()) {
				path = path.toDirectoryPath(attributes.isDirectory());
			}
		}
		return attributes;
	}
	
	@Override
	public ServerResourceProvider getProvider() {
		return provider;
	}

	@Override
	public Path getPath() {
		return path;
	}

	@Override
	public String getName() {
		return path.getFileName().toString();
	}
	
	@Override
	public ServerResource getParent() {
		ServerPath parent = path.getParent();
		return parent != null ? new ServerResource(provider, parent) : null;
	}

	@Override
	public Resource resolve(String path) {
		return provider.newResource(getPath().resolve(path));
	}

	@Override
	public boolean exists() throws IOException {
		return provider.exists(path);
	}

	@Override
	public boolean isDirectory() throws IOException {
		return path.isDirectory();
	}

	@Override
	public long length() throws IOException {
		return getAttributes().size();
	}

	@Override
	public long lastModified() throws IOException {
		return getAttributes().lastModifiedTime().toMillis();
	}

	@Override
	public void delete() throws IOException {
		provider.delete(path);
		attributes = null;
	}

	private boolean mkdirs0() throws IOException {
		if (!exists()) {
			ServerResource parent = getParent();
			if (parent != null && parent.mkdirs0()) {
				provider.createDirectory(path);
				return true;
			}
			return false;
		}
		return true;
	}
	
	@Override
	public void mkdirs() throws IOException {
		if (!mkdirs0()) {
			throw new IOException("Cannot create directory " + getPath());
		}
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return provider.getInputStream(path);
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return provider.getOutputStream(path);
	}

	@Override
	public DirectoryStream<Resource> newResourceDirectoryStream() throws IOException {
		return newResourceDirectoryStream(new DirectoryStream.Filter<Path>() {
			@Override
			public boolean accept(Path entry) throws IOException {
				return true;
			}
		});
	}

	@Override
	public DirectoryStream<Resource> newResourceDirectoryStream(final Filter<Path> filter) throws IOException {
		return provider.newResourceDirectoryStream(path, filter);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		return path.equals(((ServerResource)obj).path);
	}

	@Override
	public String toString() {
		return path.toString();
	}
}
