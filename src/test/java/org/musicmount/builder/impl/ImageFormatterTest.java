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

import javax.imageio.ImageIO;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.musicmount.builder.model.Library;
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.ProgressHandler;

public class ImageFormatterTest {
    @Rule
    public TemporaryFolder outputFolder = new TemporaryFolder();

    @Test
	public void test() throws Exception {
		ResourceProvider resourceProvider = new FileResourceProvider();

		File input = new File(getClass().getResource("/sample-album").toURI()); // has a square image
		AssetStore assetStore = new AssetStore("test");
		AssetParser assetParser = new SimpleAssetParser();
		assetStore.update(resourceProvider.newResource(input.toPath()), assetParser, 1, ProgressHandler.NOOP);
		Library library = new LibraryParser().parse(assetStore.assets());
		File output = outputFolder.getRoot();
		ResourceLocator resourceLocator = new SimpleResourceLocator(resourceProvider.newResource(output.toPath()), false, false);
		ImageFormatter imageFormatter = new ImageFormatter(assetParser, false);
		imageFormatter.formatImages(library, resourceLocator, library.getAlbums(), 4, ProgressHandler.NOOP);
		
		BufferedImage originalImage = assetParser.extractArtwork(library.getAlbums().iterator().next().artworkAssetResource());

		/*
		 * verify dimensions of written images
		 */
		for (ImageType imageType : ImageType.values()) {
			double scaleFactor = imageType.getScaleFactor(originalImage.getWidth(), originalImage.getHeight());
			String imagePath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), imageType);
			Resource imageFile = resourceLocator.getResource(imagePath);
			Assert.assertTrue(imageFile.exists());
			BufferedImage image = ImageIO.read(imageFile.getInputStream());
			if (scaleFactor < 1.0) {
				Assert.assertEquals(Math.round(originalImage.getWidth() * scaleFactor), image.getWidth());
				Assert.assertEquals(Math.round(originalImage.getHeight() * scaleFactor), image.getHeight());
			} else { // should not have scaled up 
				Assert.assertEquals(originalImage.getWidth(), image.getWidth());
				Assert.assertEquals(originalImage.getHeight(), image.getHeight());
			}
		}
	}
}
