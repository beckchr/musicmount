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

import org.musicmount.io.Resource;

import de.odysseus.ithaka.audioinfo.AudioInfo;
import de.odysseus.ithaka.audioinfo.m4a.M4AInfo;

/**
 * M4A (MP4 audio) asset parser. 
 */
public class M4AAssetParser extends AudioInfoAssetParser {
	@Override
	public boolean isAssetPath(Path path) {
		return path.getFileName().toString().toLowerCase().endsWith(".m4a");
	}

	@Override
	protected AudioInfo getAudioInfo(Resource resource, boolean imageOnly) throws Exception {
		try (InputStream input = new BufferedInputStream(resource.getInputStream())) {
			return new M4AInfo(input);
		}
	}
}
