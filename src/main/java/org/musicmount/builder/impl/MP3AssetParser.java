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
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.musicmount.util.mp3.ID3v2Exception;
import org.musicmount.util.mp3.ID3v1Info;
import org.musicmount.util.mp3.ID3v2Info;
import org.musicmount.util.mp3.MP3Exception;
import org.musicmount.util.mp3.MP3Frame;
import org.musicmount.util.mp3.MP3Input;

/**
 * M4A (MP4 audio) asset parser. 
 */
public class MP3AssetParser implements AssetParser {
	static final Logger LOGGER = Logger.getLogger(MP3AssetParser.class.getName());

	@Override
	public boolean isAssetFile(File file) {
		return file.getName().toLowerCase().endsWith(".mp3");
	}

	@Override
	public Asset parse(final File file) throws IOException, ID3v2Exception {
		try (MP3Input data = new MP3Input(new BufferedInputStream(new FileInputStream(file)))) {
			Asset asset = new Asset(file);
			if (ID3v2Info.isID3v2StartPosition(data)) {
				ID3v2Info info = new ID3v2Info(data);
				asset.setAlbum(info.getAlbum());
				asset.setAlbumArtist(info.getAlbumArtist());
				asset.setArtist(info.getArtist());
				asset.setArtworkAvailable(info.getCover() != null);
				asset.setCompilation(info.isCompilation());
				asset.setComposer(info.getComposer());
				asset.setDiscNumber(info.getDisc() > 0 ? Integer.valueOf(info.getDisc()) : null);
				if (info.getDuration() > 0) {
					asset.setDuration(info.getDuration() > 0 ? (int)((info.getDuration() + 500) / 1000) : null);
				}
				asset.setGenre(info.getGenre());
				asset.setGrouping(info.getGrouping());
				asset.setName(info.getTitle());
				asset.setTrackNumber(info.getTrack() > 0 ? Integer.valueOf(info.getTrack()) : null);
				asset.setYear(info.getYear() > 0 ? Integer.valueOf(info.getYear()) : null);
			}
			if (asset.getDuration() == null) {
				try {					
					long duration = MP3Frame.calculateDuration(data, file.length(), new MP3Frame.StopReadCondition() {
						final long stopPosition = file.length() - 128;
						@Override
						public boolean stopRead(MP3Input data) throws IOException {
							return (data.getPosition() == stopPosition) && ID3v1Info.isID3v1StartPosition(data);
						}
					});
					asset.setDuration((int)((duration + 500) / 1000));
				} catch (MP3Exception e) {
//					System.out.println("Could not determine duration for asset:" + file + " (" + e + ")");
				}
			}
			if (asset.getName() == null || asset.getAlbum() == null || asset.getArtist() == null) {
				if (data.getPosition() <= file.length() - 128) { // position to last 128 bytes
					data.skipFully(file.length() - 128 - data.getPosition());
					if (ID3v1Info.isID3v1StartPosition(data)) {
//						System.out.println("Parsing id3v1 for asset:" + file);
						ID3v1Info info = new ID3v1Info(data);
						if (asset.getAlbum() == null) {
							asset.setAlbum(info.getAlbum());
						}
						if (asset.getArtist() == null) {
							asset.setArtist(info.getArtist());
						}
						if (asset.getGenre() == null && info.getGenre() != null) {
							asset.setGenre(info.getGenre().getDescription());
						}
						if (asset.getName() == null) {
							asset.setName(info.getTitle());
						}
						if (asset.getTrackNumber() == null && info.getTrack() > 0) {
							asset.setTrackNumber(Integer.valueOf(info.getTrack()));
						}
						if (asset.getYear() == null && info.getYear() > 0) {
							asset.setYear(Integer.valueOf(info.getYear()));
						}
					}
				}
			}
			return asset;
		}
	}

	@Override
	public BufferedImage extractArtwork(File file) throws IOException, ID3v2Exception {
		try (MP3Input data = new MP3Input(new BufferedInputStream(new FileInputStream(file)))) {
			byte[] imageData = new ID3v2Info(data).getCover();
			if (imageData != null) {
				try (InputStream input = new ByteArrayInputStream(imageData)) {
					return ImageIO.read(input);
				}
			}
		}
		return null;
	}
}
