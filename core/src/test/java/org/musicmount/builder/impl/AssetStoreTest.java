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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Paths;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Library;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.ProgressHandler;

public class AssetStoreTest {
	@Test
	public void test() throws Exception {
		ResourceProvider resourceProvider = new FileResourceProvider();

		File assetDir = new File(getClass().getResource("/sample-album/sample.mp3").toURI()).getParentFile();
		AssetStore assetStore = new AssetStore("test", resourceProvider.newResource(assetDir.toPath()));
		assetStore.update(new SimpleAssetParser(), 1, ProgressHandler.NOOP);
		Library library = new LibraryParser(true).parse(assetStore.assets());

		Assert.assertEquals(1, library.getAlbumArtists().size());
		Assert.assertEquals(1, library.getTrackArtists().size());
		Assert.assertEquals(1, library.getAlbums().size());

		Set<Album> changedAlbums = assetStore.sync(library.getAlbums());
		Assert.assertEquals(1, changedAlbums.size());
		Assert.assertEquals(3, assetStore.getEntities().size());

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assetStore.save(output);
		output.close();

		ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
		assetStore = new AssetStore("test", resourceProvider.newResource(assetDir.toPath()));
		assetStore.load(input);
		input.close();
		
		Assert.assertEquals(3, assetStore.getEntities().size());
		assetStore.update(new SimpleAssetParser(), 4, ProgressHandler.NOOP);

		library = new LibraryParser(true).parse(assetStore.assets());
		Assert.assertEquals(1, library.getAlbumArtists().size());
		Assert.assertEquals(1, library.getTrackArtists().size());
		Assert.assertEquals(1, library.getAlbums().size());

		Assert.assertEquals(3, assetStore.getEntities().size());
		changedAlbums = assetStore.sync(library.getAlbums());
		Assert.assertEquals(0, changedAlbums.size());

		Asset asset = assetStore.getAsset(resourceProvider.newResource(Paths.get(getClass().getResource("/sample-album/sample.mp3").toURI())));
		Assert.assertEquals("Sample Album", asset.getAlbum());
		Assert.assertEquals("Sample - MP3", asset.getName());
		Assert.assertTrue(asset.getResource().exists());
	}
}
