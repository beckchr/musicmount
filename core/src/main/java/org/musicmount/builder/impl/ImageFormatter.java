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
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import net.coobird.thumbnailator.Thumbnails;

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Library;
import org.musicmount.io.Resource;
import org.musicmount.util.ProgressHandler;

public class ImageFormatter {
	static final Logger LOGGER = Logger.getLogger(ImageFormatter.class.getName());

	private final AssetParser assetParser;
	private final boolean retina;
	
	public ImageFormatter(AssetParser assetParser, boolean retina) {
		this.assetParser = assetParser;
		this.retina = retina;
	}

	private void writeImage(BufferedImage image, ImageType type, OutputStream output) throws IOException {
		double scaleFactor = type.getScaleFactor(image.getWidth(), image.getHeight());
		if (retina) {
			scaleFactor = scaleFactor + scaleFactor;
		}
		BufferedImage scaledImage = null;
		if (scaleFactor < 1.0) { // scale down only
			scaledImage = Thumbnails.of(image).scale(scaleFactor).asBufferedImage();
		}
		try {
			ImageIO.write(scaledImage != null ? scaledImage : image, type.getFileType(), output);
		} finally {
			if (scaledImage != null) {
				scaledImage.flush();
			}
		}
	}

	private void deleteIfExists(Resource imageResource) {
		try {
			if (imageResource.exists()) {
				imageResource.delete();
			}
		} catch (IOException e) {
			LOGGER.warning("Could not delete image file: " + imageResource.getPath().toAbsolutePath());
		}
	}
	
	private void formatImages(BufferedImage image, Map<ImageType, Resource> targets) {
		for (Map.Entry<ImageType, Resource> targetEntry : targets.entrySet()) {
			ImageType imageType = targetEntry.getKey();
			Resource imageResource = targetEntry.getValue();
			try (OutputStream output = imageResource.getOutputStream()) {
				writeImage(image, imageType, output);
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Could not write image file: " + imageResource.getPath().toAbsolutePath(), e);
				deleteIfExists(imageResource);
			}
		}
	}

	private BufferedImage extractImage(Resource asset) {
		BufferedImage image = null;
		try {
			image = assetParser.extractArtwork(asset);
			if (image == null) {
				LOGGER.warning("Could not extract image from: " + asset);
			} else if (image.getTransparency() != Transparency.OPAQUE) {
				BufferedImage tmpImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D graphics = tmpImage.createGraphics();
				graphics.drawImage(image, 0, 0, Color.WHITE, null);
				graphics.dispose();
				image.flush();
				image = tmpImage;
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Could not extract image from: " + asset, e);
		}
		return image;
	}

	private void formatImages(Resource source, Map<ImageType, Resource> targets) {
		if (!targets.isEmpty()) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Formatting images from: " + source);
			}
			BufferedImage image = extractImage(source);
			if (image != null) {
				formatImages(image, targets);
				image.flush();
			} else { // remove existing image files
				for (Resource imageTarget : targets.values()) {
					deleteIfExists(imageTarget);
				}
			}
		}
	}
	
	private Map<Album, Map<ImageType, Resource>> collectAlbumTargets(Library library, final ResourceLocator resourceLocator, Collection<Album> changedAlbums) {
		Map<Album, Map<ImageType, Resource>> result = new HashMap<>();
		for (Album album : library.getAlbums()) {
			Resource artworkAssetResource = album.artworkAssetResource();
			if (artworkAssetResource != null) {
				Map<ImageType, Resource> targets = new HashMap<ImageType, Resource>();
				for (ImageType type : ImageType.values()) {
					String imagePath = resourceLocator.getAlbumImagePath(album, type);
					if (imagePath != null) {
						Resource resource = resourceLocator.getResource(imagePath);
						try {
							if (changedAlbums.contains(album) || !resource.exists()) {
								resource.getParent().mkdirs();
								targets.put(type, resource);
							}
						} catch (IOException e) {
							LOGGER.warning("Could not write image file: " + resource.getPath().toAbsolutePath());
						}
					}
				}
				if (!targets.isEmpty()) {
					result.put(album, targets);
				}
			}
		}
		return result;
	}
	
	public void formatImages(Library library, ResourceLocator resourceLocator, Collection<Album> changedAlbums, int maxThreads, final ProgressHandler progressHandler) {
		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Preparing images...");
		}
		final Map<Album, Map<ImageType, Resource>> albumTargets = collectAlbumTargets(library, resourceLocator, changedAlbums);
		if (progressHandler != null) {
			progressHandler.endTask();
		}
		List<Album> albums = new ArrayList<>(albumTargets.keySet());

		int numberOfAlbumsPerTask = 10;
		int numberOfAlbums = albums.size();
		int numberOfThreads = Math.min(1 + (numberOfAlbums - 1) / numberOfAlbumsPerTask, Math.min(maxThreads, Runtime.getRuntime().availableProcessors()));
		if (progressHandler != null) {
			progressHandler.beginTask(numberOfAlbums, "Formatting images...");
		}
		final int progressModulo = numberOfAlbums < 200 ? 10 : numberOfAlbums < 1000 ? 50 : 100;
		if (numberOfThreads > 1) { // run on multiple threads
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Parallel: #threads = " + numberOfThreads);
			}
			ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
			final AtomicInteger atomicCount = new AtomicInteger();
			for (int start = 0; start < numberOfAlbums; start += numberOfAlbumsPerTask) {
				final Collection<Album> albumsSlice = albums.subList(start, Math.min(numberOfAlbums, start + numberOfAlbumsPerTask));
				executor.execute(new Runnable() {
					@Override
					public void run() {
						for (Album album : albumsSlice) {
							formatImages(album.artworkAssetResource(), albumTargets.get(album));
							int count = atomicCount.getAndIncrement() + 1;
							if (progressHandler != null && count % progressModulo == 0) {
								progressHandler.progress(count, String.format("#albums = %4d", count));
							}
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
			for (Album album : albums) {
				formatImages(album.artworkAssetResource(), albumTargets.get(album));
				count++;
				if (progressHandler != null && count % progressModulo == 0) {
					progressHandler.progress(count, String.format("#albums = %4d", count));
				}
			}
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}
	}
	
	public void formatAsset(Resource asset, ImageType type, OutputStream output) throws IOException {
		BufferedImage image = extractImage(asset);
		if (image == null) {
			throw new IOException("Could not extract image from asset: " + asset);
		}
		writeImage(image, type, output);
		image.flush();
	}
}
