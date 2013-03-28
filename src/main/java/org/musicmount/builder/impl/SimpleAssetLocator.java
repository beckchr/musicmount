/*
 * Copyright 2013 Odysseus Software GmbH
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.Normalizer;

public class SimpleAssetLocator implements AssetLocator {
	private static final String FILE_SEPARATOR = System.getProperty("file.separator");
	
	/**
	 * Decode, optionally normalize, encode URI path.
	 * This is needed as <code>URI.toASCIIString()</code> always normalizes the string, which leads to not
	 * being able to restore file paths containing combining diaresis characters.
	 * Simply using <code>URI.toString()</code> and letting the client escape non-ASCII characters may be a future option.
	 * @param path may contain COMBINING DIAERESIS and unencoded/non-ASCII characters (e.g. iTunes-encoded string)
	 * @param form normalize form (may be <code>null</code>)
	 * @return URL-encoded path
	 * @throws UnsupportedEncodingException
	 */
	static String reencodeURIPath(String path, Normalizer.Form form) throws UnsupportedEncodingException {
		StringBuilder builder = new StringBuilder();
		String[] pathSegments = path.split("/+");
		for (int i = 0; i < pathSegments.length; i++) {
			if (i > 0) {
				builder.append('/');
			}
			String decodedString = URLDecoder.decode(pathSegments[i].replace("+", "%2B"), "UTF-8");
			String normalizedString = form == null ? decodedString : Normalizer.normalize(decodedString, form);
			String reencodedString = URLEncoder.encode(normalizedString, "UTF-8").replace("+", "%20");
			builder.append(reencodedString);
		}
		if (path.endsWith("/")) {
			builder.append('/');
		}
		return builder.toString();
	}
	
	private static String relativePath(String path) {
		if (path == null) {
			return "";
		}
		while (path.startsWith(FILE_SEPARATOR)) {
			path = path.substring(1);
		}
		while (path.endsWith(FILE_SEPARATOR)) {
			path = path.substring(0, path.length() - 1);
		}
		if (path.length() == 0) {
			return path;
		}
		try {
			StringBuilder builder = new StringBuilder();		
			for (String pathSegment : path.split(FILE_SEPARATOR.replace("\\", "\\\\"))) {
				pathSegment = URLEncoder.encode(pathSegment, "UTF-8").replace("+", "%20");
				builder.append(pathSegment).append('/');
			}
			return builder.toString();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("must not happen...");
		}
	}
	
	private final String basePrefix; // asset folder
	private final String pathPrefix; // asset link
	private final Normalizer.Form normalizerForm;
	
	public SimpleAssetLocator(File baseFolder, String pathPrefix, Normalizer.Form normalizerForm) throws IOException {
		// avoid URI.toASCIIString() because it does normalization -> may not be able to restore files
		// avoid File.getCanonicalPath() because Java 7 (Mac) replaces non-ASCII with '?'
		this.basePrefix = baseFolder.getAbsoluteFile().toURI().toString(); // ends with "/"
		this.pathPrefix = relativePath(pathPrefix);
		this.normalizerForm = normalizerForm;
	}

	@Override
	public String getAssetPath(File assetFile) throws IOException {
		// avoid URI.toASCIIString() because it always does normalization -> may not be able to restore files
		// avoid File.getCanonicalPath() because Java 7 (Mac) replaces non-ASCII with '?'
		String assetPath = assetFile.getAbsoluteFile().toURI().toString();
		if (assetPath.startsWith(basePrefix)) { // track lives below base folder?
			String pathSuffix = assetPath.substring(basePrefix.length());
			return reencodeURIPath(pathPrefix + pathSuffix, normalizerForm);
		}
		return null;
	}
	
	@Override
	public File getAssetFile(String assetPath) {
		if (assetPath.startsWith(pathPrefix)) {
			String fullPath = basePrefix + assetPath.substring(pathPrefix.length());
			return new File(URI.create(fullPath));
		}
		return null;
	}
}
