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

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Library;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.ProgressHandler;

/*
 * TODO test doesn't assert anything
 */
public class ResponseFormatterTest {
    @Rule
    public TemporaryFolder outputFolder = new TemporaryFolder();

	@Test
	public void test() throws Exception {
		ResourceProvider resourceProvider = new FileResourceProvider();

		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(resourceProvider.newResource(outputFolder.getRoot().toPath()), false, false, false);
		ResponseFormatter.JSON responseFormatter = new ResponseFormatter.JSON("test", new LocalStrings(), false, false, false, true);

		File inputFolder = new File(getClass().getResource("/sample-library").toURI());
		AssetStore assetStore = new AssetStore("test", resourceProvider.newResource(inputFolder.toPath()));
		assetStore.update(new SimpleAssetParser(), 1, ProgressHandler.NOOP);
		Library library = new LibraryParser(true).parse(assetStore.assets());

		SimpleAssetLocator assetLocator = new SimpleAssetLocator(resourceProvider.newResource(outputFolder.getRoot().toPath()), "music", null);
		ByteArrayOutputStream output;
		
		output = new ByteArrayOutputStream();
		responseFormatter.formatAlbum(library.getAlbums().get(0), output, resourceLocator, assetLocator);

		output = new ByteArrayOutputStream();
		responseFormatter.formatAlbumCollection(library.getAlbumArtists().values().iterator().next(), output, resourceLocator);

		output = new ByteArrayOutputStream();
		responseFormatter.formatAlbumCollection(library.getTrackArtists().values().iterator().next(), output, resourceLocator);

		output = new ByteArrayOutputStream();
		responseFormatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);

		output = new ByteArrayOutputStream();
		responseFormatter.formatArtistIndex(library.getAlbumArtists().values(), ArtistType.AlbumArtist, output, resourceLocator, null);

		output = new ByteArrayOutputStream();
		responseFormatter.formatArtistIndex(library.getTrackArtists().values(), ArtistType.TrackArtist, output, resourceLocator, null);

		output = new ByteArrayOutputStream();
		responseFormatter.formatTrackIndex(library.getTracks(), output, resourceLocator, assetLocator);

		output = new ByteArrayOutputStream();
		responseFormatter.formatServiceIndex(resourceLocator, output);
	}
}
