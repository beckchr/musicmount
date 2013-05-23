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
import java.io.InputStream;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.musicmount.util.mp4.M4AInfo;
import org.musicmount.util.mp4.MP4Input;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;

public class SimpleAssetParser implements AssetParser {
	static final Logger LOGGER = Logger.getLogger(SimpleAssetParser.class.getName());

	protected boolean isMP3File(File file) {
		return file.getName().toLowerCase().endsWith(".mp3");
	}
	
	protected boolean isM4AFile(File file) {
		return file.getName().toLowerCase().endsWith(".m4a");
	}
	
	@Override
	public boolean isAssetFile(File file) {
		return isMP3File(file) || isM4AFile(file);
	}

	public Asset parse(File assetFile) throws Exception {
		if (isMP3File(assetFile)) {
			return parseMP3(assetFile);
		} else if (isM4AFile(assetFile)) {
			return parseM4A(assetFile);
		} else {
			throw new RuntimeException("Not an asset: " + assetFile);
		}
	}

	public Asset parseM4A(File assetFile) throws Exception {
		try (MP4Input input = new MP4Input(new BufferedInputStream(new FileInputStream(assetFile)))) {
			M4AInfo info = new M4AInfo(input);
			Asset asset = new Asset(assetFile);
			asset.setAlbum(info.getAlbum());
			asset.setAlbumArtist(info.getAlbumArtist());
			asset.setArtist(info.getArtist());
			asset.setArtworkAvailable(info.getCover() != null);
			asset.setCompilation(info.isCompilation());
			asset.setComposer(info.getComposer());
			asset.setDiscNumber(info.getDisc() > 0 ? Integer.valueOf(info.getDisc()) : null);
			asset.setDuration(info.getDuration() > 0 ? (int)((info.getDuration() + 500) / 1000) : null);
			asset.setGenre(info.getGenre());
			asset.setName(info.getTitle());
			asset.setTrackNumber(info.getTrack() > 0 ? Integer.valueOf(info.getTrack()) : null);
			asset.setYear(info.getYear() > 0 ? Integer.valueOf(info.getYear()) : null);
			return asset;
		}
	}
	
	public Asset parseMP3(File assetFile) throws Exception {
		Asset asset = new Asset(assetFile);
		Mp3File mp3file = new Mp3File(assetFile.getAbsolutePath());
		asset.setDuration(mp3file.getLengthInSeconds() > 0 ? (int)mp3file.getLengthInSeconds() : null);
	    if (mp3file.hasId3v2Tag()) {
	    	ID3v2 info = mp3file.getId3v2Tag();
			asset.setAlbum(info.getAlbum());
			asset.setAlbumArtist(info.getAlbumArtist());
			asset.setArtist(info.getArtist());
			asset.setArtworkAvailable(info.getAlbumImage() != null);
			asset.setCompilation(info.isCompilation());
			asset.setComposer(info.getComposer());
			if (info.getPartOfSet() != null && info.getPartOfSet().trim().length() > 0) {
				String string = info.getPartOfSet().trim();
				int index = string.indexOf('/');
				if (index > 0) {
					string = string.substring(0, index);
				}
				try {
					asset.setDiscNumber(Integer.valueOf(string));
				} catch (NumberFormatException e) {
					LOGGER.warning("Could not parse disc number: " + info.getPartOfSet() + " (" + assetFile + ")");
				}
			}
			asset.setGenre(info.getGenreDescription());
			asset.setName(info.getTitle());
			if (info.getTrack() != null && info.getTrack().trim().length() > 0) {
				String string = info.getTrack().trim();
				int index = string.indexOf('/');
				if (index > 0) {
					string = string.substring(0, index);
				}
				try {
					asset.setTrackNumber(Integer.valueOf(string));
				} catch (NumberFormatException e) {
					LOGGER.warning("Could not parse track number: " + info.getTrack() + " (" + assetFile + ")");
				}
			}
			if (info.getYear() != null && info.getYear().trim().length() > 0) {
				try {
					asset.setYear(Integer.valueOf(info.getYear()));
				} catch (NumberFormatException e) {
					LOGGER.warning("Could not parse year: " + info.getYear() + " (" + assetFile + ")");
				}
			}
	    } else if (mp3file.hasId3v1Tag()) {
	    	ID3v1 info = mp3file.getId3v1Tag();
			asset.setAlbum(info.getAlbum());
			asset.setAlbumArtist(null);
			asset.setArtist(info.getArtist());
			asset.setArtworkAvailable(false);
			asset.setCompilation(false);
			asset.setComposer(null);
			asset.setDiscNumber(null);
			asset.setGenre(info.getGenreDescription());
			asset.setName(info.getTitle());
			asset.setTrackNumber(null);
			if (info.getYear() != null && info.getYear().trim().length() > 0) {
				try {
					asset.setYear(Integer.valueOf(info.getYear()));
				} catch (NumberFormatException e) {
					LOGGER.warning("Could not parse year: " + info.getYear() + " (" + assetFile + ")");
				}
			}
	    }
	    return asset;
	}
	
	@Override
	public BufferedImage extractArtwork(File assetFile) throws Exception {
		byte[] imageData = null;
		if (isMP3File(assetFile)) {
			Mp3File mp3file = new Mp3File(assetFile.getAbsolutePath());
		    if (mp3file.hasId3v2Tag()) {
				imageData =  mp3file.getId3v2Tag().getAlbumImage();
		    }
		} else if (isM4AFile(assetFile)) {
			try (MP4Input input = new MP4Input(new BufferedInputStream(new FileInputStream(assetFile)))) {
				imageData = new M4AInfo(input).getCover();
			}
		}
		if (imageData != null) {
			try (InputStream input = new ByteArrayInputStream(imageData)) {
				return ImageIO.read(input);
			}
		}
		return null;
	}
}
