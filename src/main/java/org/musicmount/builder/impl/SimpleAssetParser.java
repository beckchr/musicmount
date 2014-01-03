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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Delegating asset parser. 
 */
public class SimpleAssetParser implements AssetParser {
	private final List<AssetParser> delegates;
	
	/**
	 * Create instance capable of MP3, M4A parsing
	 */
	public SimpleAssetParser() {
		this(new MP3AssetParser(), new M4AAssetParser());
	}
	
	public SimpleAssetParser(AssetParser... delegates) {
		this.delegates = Arrays.asList(delegates);
	}
	
	protected AssetParser getDelegate(File file) {
		for (AssetParser delegate : delegates) {
			if (delegate.isAssetFile(file)) {
				return delegate;
			}
		}
		return null;
	}
	
	@Override
	public boolean isAssetFile(File file) {
		return getDelegate(file) != null;
	}

	public Asset parse(File file) throws Exception {
		AssetParser delegate = getDelegate(file);
		if (delegate != null) {
			return delegate.parse(file);
		} else {
			throw new IllegalArgumentException("Not an asset: " + file);
		}
	}
	
	@Override
	public BufferedImage extractArtwork(File file) throws Exception {
		AssetParser delegate = getDelegate(file);
		if (delegate != null) {
			return delegate.extractArtwork(file);
		} else {
			throw new IllegalArgumentException("Not an asset: " + file);
		}
	}
}
