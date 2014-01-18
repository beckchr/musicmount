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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;

public abstract class ServerResourceProvider implements ResourceProvider {
	private final ServerFileSystem fileSystem;

	protected ServerResourceProvider(ServerFileSystem fileSystem) {
		this.fileSystem = fileSystem;
	}

	protected abstract BasicFileAttributes getFileAttributes(ServerPath path) throws IOException;
	protected abstract List<ServerFileAttributes> getChildrenAttributes(ServerPath folder) throws IOException;
	protected abstract boolean exists(ServerPath path) throws IOException;
	protected abstract void delete(ServerPath path) throws IOException;
	protected abstract void createDirectory(ServerPath path) throws IOException;
	protected abstract InputStream getInputStream(ServerPath path) throws IOException;
	protected abstract OutputStream getOutputStream(ServerPath path) throws IOException;

	@Override
	public boolean isDirectory(Path path) throws IOException {
		return ((ServerPath) path).isDirectory();
	}
	
	@Override
	public Resource getBaseDirectory() {
		return newResource(fileSystem.getBaseDirectory());
	}

	@Override
	public ServerResource newResource(Path path) {
		if (!fileSystem.equals(path.getFileSystem())) {
			throw new IllegalArgumentException("Invalid path");
		}
		return new ServerResource(this, (ServerPath) path);
	}

	@Override
	public ServerResource newResource(String first, String... more) {
		return new ServerResource(this, fileSystem.getPath(first, more));
	}

	DirectoryStream<Resource> newResourceDirectoryStream(final ServerPath path, final Filter<Path> filter) throws IOException {
		return new DirectoryStream<Resource>() {
			final List<ServerFileAttributes> list = getChildrenAttributes(path);
			@Override
			public void close() throws IOException {
			}

			@Override
			public Iterator<Resource> iterator() {
				return new Iterator<Resource>() {
					final Iterator<ServerFileAttributes> delegate = list.iterator();
					Resource next = findNext();
					Resource findNext() {
						while (delegate.hasNext()) {
							ServerFileAttributes attributes = delegate.next();
							String p = attributes.getPath();
							if (attributes.isDirectory() && !p.endsWith(fileSystem.getSeparator())) {
								p += fileSystem.getSeparator();
							}
							ServerPath serverPath = fileSystem.getPath(p);
							try {
								if (filter.accept(serverPath)) {
									return new ServerResource(ServerResourceProvider.this, serverPath, attributes);
								}
							} catch (IOException e) {
								return null;
							}
						}
						return null;
					}
					@Override
					public boolean hasNext() {
						return next != null;
					}
					@Override
					public Resource next() {
						if (next == null) {
							throw new NoSuchElementException();
						}
						Resource result = next;
						next = findNext();
						return result;
					}
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@Override
	public String toString() {
		return getBaseDirectory().getPath().toUri().toString();
	}
}
