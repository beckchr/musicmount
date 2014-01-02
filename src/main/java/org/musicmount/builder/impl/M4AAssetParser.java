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

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.musicmount.util.mp4.M4AInfo;

/**
 * M4A (MP4 audio) asset parser. 
 */
public class M4AAssetParser implements AssetParser {
	@Override
	public boolean isAssetFile(File file) {
		return file.getName().toLowerCase().endsWith(".m4a");
	}

	@Override
	public Asset parse(File file) throws IOException {
		try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
			M4AInfo info = new M4AInfo(input);
			Asset asset = new Asset(file);
			asset.setAlbum(info.getAlbum());
			asset.setAlbumArtist(info.getAlbumArtist());
			asset.setArtist(info.getArtist());
			asset.setArtworkAvailable(info.getCover() != null);
			asset.setCompilation(info.isCompilation());
			asset.setComposer(info.getComposer());
			asset.setDiscNumber(info.getDisc() > 0 ? Integer.valueOf(info.getDisc()) : null);
			asset.setDuration(info.getDuration() > 0 ? (int)((info.getDuration() + 500) / 1000) : null);
			asset.setGenre(info.getGenre());
			asset.setGrouping(info.getGrouping());
			asset.setName(info.getTitle());
			asset.setTrackNumber(info.getTrack() > 0 ? Integer.valueOf(info.getTrack()) : null);
			asset.setYear(info.getYear() > 0 ? Integer.valueOf(info.getYear()) : null);
			return asset;
		}
	}

	@Override
	public BufferedImage extractArtwork(File file) throws IOException {
		try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
			byte[] imageData = new M4AInfo(input).getCover();
			if (imageData != null) {
				try (InputStream imageDataInput = new ByteArrayInputStream(imageData)) {
					return ImageIO.read(imageDataInput);
				}
			}
			return null;
		}
	}
}
