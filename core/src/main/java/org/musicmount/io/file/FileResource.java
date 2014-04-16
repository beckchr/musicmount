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
package org.musicmount.io.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import org.musicmount.io.Resource;
import org.musicmount.io.ResourceDirectoryStream;
import org.musicmount.io.ResourceProvider;

public class FileResource implements Resource {
	private final FileResourceProvider provider; 
	private final Path path;

	FileResource(FileResourceProvider provider, Path path) {
		this.provider = provider;
		this.path = path;
	}
	
	@Override
	public ResourceProvider getProvider() {
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
	public FileResource getParent() {
		Path parentPath = path.getParent();
		if (parentPath == null) {
			return null;
		}
		return new FileResource(provider, parentPath);
	}

	@Override
	public FileResource resolve(String path) {
		return provider.newResource(getPath().resolve(path));
	}

	@Override
	public boolean exists() {
		return Files.exists(path);
	}

	@Override
	public boolean isDirectory() {
		return Files.isDirectory(path);
	}

	@Override
	public long lastModified() throws IOException {
		return Files.getLastModifiedTime(path).toMillis();
	}
	
	@Override
	public long length() throws IOException {
		return Files.size(path);
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return Files.newInputStream(path);
	}
	
	@Override
	public OutputStream getOutputStream() throws IOException {
		return Files.newOutputStream(path);
	}
	
	@Override
	public int hashCode() {
		return path.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		return path.equals(((FileResource)obj).path);
	}
	
	@Override
	public void delete() throws IOException {
		Files.deleteIfExists(getPath());
	}
	
	@Override
	public void mkdirs() throws IOException {
		Files.createDirectories(getPath());
	}

	@Override
	public ResourceDirectoryStream newResourceDirectoryStream() throws IOException {
		return new ResourceDirectoryStream(provider, Files.newDirectoryStream(getPath()));
	}

	@Override
	public ResourceDirectoryStream newResourceDirectoryStream(Filter<Path> filter) throws IOException {
		return new ResourceDirectoryStream(provider, Files.newDirectoryStream(getPath(), filter));
	}

	@Override
	public String toString() {
		return path.toString();
	}
}
