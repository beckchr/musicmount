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

import java.awt.AWTError;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.musicmount.audio.AudioInfo;
import org.musicmount.io.Resource;

/**
 * Generic asset parser based on. 
 */
public abstract class AudioInfoAssetParser implements AssetParser {
	static final Toolkit TOOLKIT;
	static {
		Toolkit toolkit = null;
		try {
			toolkit = Toolkit.getDefaultToolkit();
		} catch (AWTError e) {
			System.err.println("AWT toolkit not available: " + e);
		} finally {
			TOOLKIT = toolkit;
		}
	}
	
	/**
	 * Do the magic...
	 * @param resource audio file
	 * @param imageOnly <code>true</code> if only interested in cover image
	 * @return audio info
	 * @throws Exception something went wrong
	 */
	protected abstract AudioInfo getAudioInfo(Resource resource, boolean imageOnly) throws Exception;

	@Override
	public Asset parse(Resource resource) throws Exception {
		AudioInfo info = getAudioInfo(resource, false);
		Asset asset = new Asset(resource);
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

	/**
	 * Create image from bytes using ImageIO.
	 * @param cover image bytes
	 * @return buffered image
	 */
	BufferedImage toBufferedImageUsingImageIO(byte[] bytes) throws IOException {
		try (InputStream data = new ByteArrayInputStream(bytes)) {
			return ImageIO.read(data);
		}
	}

	/**
	 * Create image from bytes using AWT toolkit.
	 * Seems to be faster than ImageIO... 
	 * @param cover image bytes
	 * @return buffered image
	 */
	BufferedImage toBufferedImageUsingToolkit(byte[] bytes) throws IOException {
		if (TOOLKIT == null) {
			return null;
		}
		Image image = TOOLKIT.createImage(bytes);
		MediaTracker mediaTracker = new MediaTracker(new Component() {
			private static final long serialVersionUID = 1L;
		});
		mediaTracker.addImage(image, 0);
		try {
			mediaTracker.waitForID(0, 0);
		} catch (InterruptedException e) {
			return null;
		}
		if (mediaTracker.isErrorID(0)) { // error -> use ImageIO
			throw new IOException("Failed to load toolkit image");
		} else {
			BufferedImage bufferedImage =
					new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image.flush();
            return bufferedImage;
		}
	}

	@Override
	public BufferedImage extractArtwork(Resource resource) throws Exception {
		byte[] cover = getAudioInfo(resource, true).getCover();
		if (cover != null) {
			BufferedImage bufferedImage = null;
			try {
				bufferedImage = toBufferedImageUsingToolkit(cover);
			} catch (Throwable e) {
				// ignore
			}
			if (bufferedImage == null) {
				bufferedImage = toBufferedImageUsingImageIO(cover);
			}
			return bufferedImage;
		}
		return null;
	}
}
