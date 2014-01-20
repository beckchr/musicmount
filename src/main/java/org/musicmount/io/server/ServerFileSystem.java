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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Set;

public class ServerFileSystem extends FileSystem {
	private final String scheme;
	private final String host;
	private final int port;
	private final String userInfo;

	private final ServerPath rootDirectory;
	private final ServerPath baseDirectory;

	public ServerFileSystem(URI serverUri) {
		this(serverUri, serverUri.getUserInfo());
	}

	public ServerFileSystem(URI serverUri, String userInfo) {
		this.scheme = serverUri.getScheme();
		this.userInfo = serverUri.getUserInfo();
		this.host = serverUri.getHost();
		this.port = serverUri.getPort();

		String path = serverUri.getPath();
		if (path == null) {
			path = "";
		}
		if (!path.startsWith(getSeparator())) {
			path = getSeparator() + path;
		}
		if (!path.endsWith(getSeparator())) {
			path += getSeparator();
		}
		this.baseDirectory = new ServerPath(this, path);
		this.rootDirectory = new ServerPath(this, getSeparator());
	}

	public ServerFileSystem(String scheme, String authority, String path) throws URISyntaxException {
		this(new URI(scheme, authority, path, null, null));
	}

	public ServerFileSystem(String scheme, String host, int port, String path, String user, String password) throws URISyntaxException {
		this(new URI(scheme, (user != null ? user + ":" + password : null), host, port, path, null, null));
	}
	
	public URI getServerUri(ServerPath path) throws URISyntaxException {
		return new URI(scheme, null, host, port, path.toAbsolutePath().normalize().toString(), null, null);
	}
	
	public ServerPath getBaseDirectory() {
		return baseDirectory;
	}
	
	public ServerPath getRootDirectory() {
		return rootDirectory;
	}
	
	public String getScheme() {
		return scheme;
	}
	
	public String getUserInfo() {
		return userInfo;
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}

	@Override
	public String getSeparator() {
		return "/";
	}
	
	@Override
	public FileSystemProvider provider() {
		return null;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Collections.<Path>singletonList(rootDirectory);
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return Collections.emptyList();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return Collections.singleton("basic");
	}

	@Override
	public ServerPath getPath(String first, String... more) {
		return new ServerPath(this, first, more);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WatchService newWatchService() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
}
