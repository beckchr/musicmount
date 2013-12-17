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

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Library;

/*
 * TODO test doesn't assert anything
 */
public class ResponseFormatterTest {
    @Rule
    public TemporaryFolder outputFolder = new TemporaryFolder();

	@Test
	public void test() throws Exception {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(outputFolder.getRoot(), false, false);
		ResponseFormatter.JSON responseFormatter = new ResponseFormatter.JSON("test", new LocalStrings(), false, false, false, true);

		File inputFolder = new File(getClass().getResource("/sample-library").toURI());
		AssetStore assetStore = new AssetStore("test");
		assetStore.update(inputFolder, new SimpleAssetParser());
		Library library = new LibraryParser().parse(assetStore.assets());

		SimpleAssetLocator assetLocator = new SimpleAssetLocator(outputFolder.getRoot(), "music", null);
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
		responseFormatter.formatServiceIndex(resourceLocator, output);
	}
}
