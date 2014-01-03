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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Library;

public class ImageFormatter {
	static final Logger LOGGER = Logger.getLogger(ImageFormatter.class.getName());

	private final AssetParser assetParser;
	private final boolean retina;
	
	public ImageFormatter(AssetParser assetParser, boolean retina) {
		this.assetParser = assetParser;
		this.retina = retina;
	}
	
	private void writeImage(BufferedImage image, ImageType type, File targetFile, boolean retina) throws IOException {
		double scaleFactor = type.getScaleFactor(image.getWidth(), image.getHeight());
		if (retina) {
			scaleFactor = scaleFactor + scaleFactor;
		}
		BufferedImage scaledImage = null;
		if (scaleFactor < 1.0) { // scale down only
			scaledImage = Thumbnails.of(image).scale(scaleFactor).asBufferedImage();
		}
		try {
	        ImageIO.write(scaledImage != null ? scaledImage : image, type.getFileType(), targetFile);
		} finally {
			if (scaledImage != null) {
				scaledImage.flush();
			}
		}
	}

	private void formatImages(BufferedImage image, Map<ImageType, File> targets) {
		for (Map.Entry<ImageType, File> targetEntry : targets.entrySet()) {
			ImageType imageType = targetEntry.getKey();
			File imageFile = targetEntry.getValue();
			imageFile.getParentFile().mkdirs();
			try {
				writeImage(image, imageType, imageFile, retina);
			} catch (IOException e) {
				LOGGER.warning("Could not write image file: " + imageFile.getAbsolutePath());
				if (imageFile.exists()) {
					imageFile.delete();
				}
			}
		}
	}

	private void formatAlbumImages(Album album, ResourceLocator resourceLocator, boolean overwrite) {
		File artworkAssetFile = album.artworkAssetFile();
		if (artworkAssetFile != null) {
			Map<ImageType, File> targets = new HashMap<ImageType, File>();
			for (ImageType type : ImageType.values()) {
				String imagePath = resourceLocator.getAlbumImagePath(album, type);
				if (imagePath != null) {
					File file = resourceLocator.getFile(imagePath);
					if (overwrite || !file.exists()) {
						targets.put(type, file);
					}
				}
			}
			if (!targets.isEmpty()) {
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("Formatting images from assset: " + artworkAssetFile);
				}
		    	BufferedImage image;
		        try {
		        	image = assetParser.extractArtwork(artworkAssetFile);
		    		if (image.getTransparency() != Transparency.OPAQUE) {
		    			BufferedImage tmpImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		    			Graphics2D graphics = tmpImage.createGraphics();
		    			graphics.drawImage(image, 0, 0, Color.WHITE, null);
		    			graphics.dispose();
		    			image.flush();
		    			image = tmpImage;
		    		}
		        } catch(Exception e) {
		        	LOGGER.log(Level.WARNING, "Could not extract image from " + artworkAssetFile, e);
		        	return;
		        }
		        formatImages(image, targets);
				image.flush();
			}
		}
	}
	
	public void formatImages(Library library, final ResourceLocator resourceLocator, final Collection<Album> overwriteAlbums) {
		int numberOfAlbumsPerTask = 100;
		int numberOfAlbums = library.getAlbums().size();
		int numberOfThreads = Math.min(1 + (numberOfAlbums - 1) / numberOfAlbumsPerTask, Runtime.getRuntime().availableProcessors());
		if (numberOfThreads > 1) { // run on multiple threads
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine("Parallel: #threads = " + numberOfThreads);
			}
			ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
			for (int start = 0; start < numberOfAlbums; start += numberOfAlbumsPerTask) {
				final Collection<Album> albums = library.getAlbums().subList(start, Math.min(numberOfAlbums, start + numberOfAlbumsPerTask));
				executor.execute(new Runnable() {
					@Override
					public void run() {
						for (Album album : albums) {
							formatAlbumImages(album, resourceLocator, overwriteAlbums.contains(album));
						}
						if (LOGGER.isLoggable(Level.FINE)) {
							LOGGER.fine(String.format("Progress: #albums += %3d", albums.size()));
						}
					}
				});
			}
			executor.shutdown();
			try {
				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				LOGGER.warning("Interrupted: " + e.getMessage());
			}
		} else { // run on current thread
			int count = 0;
			for (Album album : library.getAlbums()) {
				formatAlbumImages(album, resourceLocator, overwriteAlbums.contains(album));
				if (++count % 100 == 0 && LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine(String.format("Progress: #albums = %4d", count));
				}
			}
		}
	}
}
