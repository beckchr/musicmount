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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Path;

import org.musicmount.audio.AudioInfo;
import org.musicmount.audio.mp3.ID3v2Info;
import org.musicmount.audio.mp3.MP3Info;
import org.musicmount.io.Resource;

/**
 * MP3 asset parser. 
 */
public class MP3AssetParser extends AudioInfoAssetParser {
	@Override
	public boolean isAssetPath(Path resource) {
		return resource.getFileName().toString().toLowerCase().endsWith(".mp3");
	}

	@Override
	protected AudioInfo getAudioInfo(Resource resource, boolean imageOnly) throws Exception {
		try (InputStream input = new BufferedInputStream(resource.getInputStream())) {
			return imageOnly ? new ID3v2Info(input) : new MP3Info(input, resource.length());
		}
	}
}
