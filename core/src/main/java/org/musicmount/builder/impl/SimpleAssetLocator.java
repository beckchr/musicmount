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
package org.musicmount.builder.impl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.text.Normalizer;

import org.musicmount.io.Resource;
import org.musicmount.io.server.ServerFileSystem;
import org.musicmount.io.server.ServerPath;

public class SimpleAssetLocator implements AssetLocator {
	/**
	 * Optionally normalize, encode URI path.
	 * This is needed as <code>URI.toASCIIString()</code> always normalizes the string, which leads to not
	 * being able to restore file paths containing combining diaresis characters.
	 * Simply using <code>URI.toString()</code> and letting the client escape non-ASCII characters may be a future option.
	 * @param path may contain COMBINING DIAERESIS and unencoded/non-ASCII characters (e.g. iTunes-encoded string)
	 * @param form normalize form (may be <code>null</code>)
	 * @return URL-encoded path
	 * @throws UnsupportedEncodingException
	 */
	static String encodeURIPath(ServerPath path, Normalizer.Form form) throws UnsupportedEncodingException {
		StringBuilder builder = new StringBuilder();
		if (path.isAbsolute()) {
			builder.append('/');
		}
		for (int i = 0; i < path.getNameCount(); i++) {
			if (i > 0) {
				builder.append('/');
			}
			String decodedString = path.getName(i).toString();
			String normalizedString = form == null ? decodedString : Normalizer.normalize(decodedString, form);
			String reencodedString = URLEncoder.encode(normalizedString, "UTF-8").replace("+", "%20");
			builder.append(reencodedString);
		}
		if (path.isDirectory()) {
			builder.append('/');
		}
		return builder.toString();
	}

	private final Path basePath;
	private final ServerPath serverPath;
	private final Normalizer.Form normalizerForm;
	private final FileSystem fileSystem;	
	
	public SimpleAssetLocator(Resource baseFolder, String pathPrefix, Normalizer.Form normalizerForm) {
		this.fileSystem = baseFolder.getPath().getFileSystem();
		this.basePath = baseFolder.getPath().toAbsolutePath();
		this.serverPath = new ServerPath(new ServerFileSystem(URI.create("/")), pathPrefix(pathPrefix));
		this.normalizerForm = normalizerForm;
	}

	private String pathPrefix(String path) {
		if (path == null) {
			return "";
		}
		path = path.trim();
		if (path.length() == 0) {
			return path;
		}
		path = path.replace(fileSystem.getSeparator(), "/").replaceAll("/+", "/");
		if (!path.endsWith("/")) {
			path += "/";
		}
		return path;
	}

	@Override
	public String getAssetPath(Resource assetResource) throws UnsupportedEncodingException {
		Path path = assetResource.getPath().toAbsolutePath();
		if (!path.startsWith(basePath)) {
			return null;
		}
		try {
			path = basePath.relativize(path);
		} catch (IllegalArgumentException e) {
			return null;
		}
		String pathString = path.toString().replace(fileSystem.getSeparator(), "/");
		return encodeURIPath(serverPath.resolve(pathString), normalizerForm);
	}
}
