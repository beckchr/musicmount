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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.musicmount.audio.AudioInfo;

/**
 * Generic asset parser based on. 
 */
public abstract class AudioInfoAssetParser implements AssetParser {
	/**
	 * Do the magic...
	 * @param file
	 * @return audio info
	 * @throws Exception
	 */
	protected abstract AudioInfo getAudioInfo(File file) throws Exception;

	@Override
	public Asset parse(File file) throws Exception {
		AudioInfo info = getAudioInfo(file);
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

	@Override
	public BufferedImage extractArtwork(File file) throws Exception {
		byte[] imageData = getAudioInfo(file).getCover();
		if (imageData != null) {
			try (InputStream imageDataInput = new ByteArrayInputStream(imageData)) {
				return ImageIO.read(imageDataInput);
			}
		}
		return null;
	}
}
