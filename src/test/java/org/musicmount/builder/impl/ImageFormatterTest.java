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
import java.io.File;

import javax.imageio.ImageIO;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.musicmount.builder.model.Library;

public class ImageFormatterTest {
    @Rule
    public TemporaryFolder outputFolder = new TemporaryFolder();

    @Test
	public void testBasic() throws Exception {
		File input = new File(getClass().getResource("/sample-album").toURI()); // has a square image
		AssetParser assetParser = new SimpleAssetParser();
		Library library = new LibraryParser(assetParser).parse(input, new AssetStore("test"));
		File output = outputFolder.getRoot();
		ResourceLocator resourceLocator = new SimpleResourceLocator(output, false, false);
		ImageFormatter imageFormatter = new ImageFormatter(assetParser, false);
		imageFormatter.formatImages(library, resourceLocator, new AssetStore("test"));
		
		String artworkPath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Artwork);
		File artworkFile = resourceLocator.getFile(artworkPath);
		Assert.assertTrue(artworkFile.exists());
		BufferedImage artworkImage = ImageIO.read(artworkFile);
		Assert.assertEquals(ImageType.Artwork.getMaxSize().width, artworkImage.getWidth());
		Assert.assertEquals(ImageType.Artwork.getMaxSize().width, artworkImage.getHeight());
		
		String tilePath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Tile);
		File tileFile = resourceLocator.getFile(tilePath);
		Assert.assertTrue(tileFile.exists());
		BufferedImage tileImage = ImageIO.read(tileFile);
		Assert.assertEquals(ImageType.Tile.getMaxSize().height, tileImage.getWidth());
		Assert.assertEquals(ImageType.Tile.getMaxSize().height, tileImage.getHeight());
		
		String thumbnailPath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Thumbnail);
		File thumbnailFile = resourceLocator.getFile(thumbnailPath);
		Assert.assertTrue(thumbnailFile.exists());
		BufferedImage thumbnailImage = ImageIO.read(thumbnailFile);
		Assert.assertEquals(ImageType.Thumbnail.getMaxSize().width, thumbnailImage.getWidth());
		Assert.assertEquals(ImageType.Thumbnail.getMaxSize().height, thumbnailImage.getHeight());
	}

    @Test
	public void testRetina() throws Exception {
		File input = new File(getClass().getResource("/sample-album").toURI()); // has a square image
		AssetParser assetParser = new SimpleAssetParser();
		Library library = new LibraryParser(assetParser).parse(input, new AssetStore("test"));
		File output = outputFolder.getRoot();
		ResourceLocator resourceLocator = new SimpleResourceLocator(output, false, false);
		ImageFormatter imageFormatter = new ImageFormatter(assetParser, true); // retina
		imageFormatter.formatImages(library, resourceLocator, new AssetStore("test"));
		
		String artworkPath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Artwork);
		File artworkFile = resourceLocator.getFile(artworkPath);
		Assert.assertTrue(artworkFile.exists());
		BufferedImage artworkImage = ImageIO.read(artworkFile);
		Assert.assertEquals(2 * ImageType.Artwork.getMaxSize().width, artworkImage.getWidth());
		Assert.assertEquals(2 * ImageType.Artwork.getMaxSize().width, artworkImage.getHeight());
		
		String tilePath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Tile);
		File tileFile = resourceLocator.getFile(tilePath);
		Assert.assertTrue(tileFile.exists());
		BufferedImage tileImage = ImageIO.read(tileFile);
		Assert.assertEquals(2 * ImageType.Tile.getMaxSize().height, tileImage.getWidth());
		Assert.assertEquals(2 * ImageType.Tile.getMaxSize().height, tileImage.getHeight());
		
		String thumbnailPath = resourceLocator.getAlbumImagePath(library.getAlbums().get(0), ImageType.Thumbnail);
		File thumbnailFile = resourceLocator.getFile(thumbnailPath);
		Assert.assertTrue(thumbnailFile.exists());
		BufferedImage thumbnailImage = ImageIO.read(thumbnailFile);
		Assert.assertEquals(2 * ImageType.Thumbnail.getMaxSize().width, thumbnailImage.getWidth());
		Assert.assertEquals(2 * ImageType.Thumbnail.getMaxSize().height, thumbnailImage.getHeight());
	}
}
