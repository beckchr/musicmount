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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.text.Normalizer;

import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;

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
	static String encodeURIPath(String path, Normalizer.Form form) throws UnsupportedEncodingException {
		StringBuilder builder = new StringBuilder();
		String[] pathSegments = path.split("/+");
		for (int i = 0; i < pathSegments.length; i++) {
			if (i > 0) {
				builder.append('/');
			}
			String decodedString = pathSegments[i];
			String normalizedString = form == null ? decodedString : Normalizer.normalize(decodedString, form);
			String reencodedString = URLEncoder.encode(normalizedString, "UTF-8").replace("+", "%20");
			builder.append(reencodedString);
		}
		if (path.endsWith("/")) {
			builder.append('/');
		}
		return builder.toString();
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

	private String filePrefix(Resource baseFolder) {
		String path = baseFolder.getPath().toAbsolutePath().toString();
		if (!path.endsWith(fileSystem.getSeparator())) {
			path += fileSystem.getSeparator();
		}
		return path;
	}

	private final String filePrefix; // asset folder
	private final String pathPrefix; // asset link
	private final Normalizer.Form normalizerForm;
	private final FileSystem fileSystem;
	private final ResourceProvider fileProvider;
	
	public SimpleAssetLocator(Resource baseFolder, String pathPrefix, Normalizer.Form normalizerForm) {
		// avoid URI.toASCIIString() because it does normalization -> may not be able to restore files
		// avoid File.getCanonicalPath() because Java 7 (Mac) replaces non-ASCII with '?'
//		this.basePrefix = baseFolder.getAbsoluteFile().toURI().toString(); // ends with "/"
		this.fileSystem = baseFolder.getPath().getFileSystem();
		this.fileProvider = baseFolder.getProvider();
		this.filePrefix = filePrefix(baseFolder); // ends with fileSystem.separator
		this.pathPrefix = pathPrefix(pathPrefix); // ends with "/"
		this.normalizerForm = normalizerForm;
	}

	@Override
	public String getAssetPath(Resource assetResource) throws UnsupportedEncodingException {
		// avoid URI.toASCIIString() because it always does normalization -> may not be able to restore files
		// avoid File.getCanonicalPath() because Java 7 (Mac) replaces non-ASCII with '?'
		String assetPath = assetResource.getPath().toAbsolutePath().toString();
		if (assetPath.startsWith(filePrefix)) { // asset lives below base folder?
			String pathSuffix = assetPath.substring(filePrefix.length());
			pathSuffix = pathSuffix.replace(fileSystem.getSeparator(), "/");
			return encodeURIPath(pathPrefix + pathSuffix, normalizerForm);
		}
		return null;
	}
	
	@Override
	public Resource getAssetResource(String assetPath) throws UnsupportedEncodingException {
		String filePath = URLDecoder.decode(assetPath.replace("+", "%2B"), "UTF-8");
		if (filePath.startsWith(pathPrefix)) {
			filePath = filePath.replace("/", fileSystem.getSeparator());
			return fileProvider.newResource(filePrefix + filePath.substring(pathPrefix.length()));
		}
		return null;
	}
}
