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
package org.musicmount.builder;

import java.io.File;
import java.net.URL;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.model.Library;
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.ProgressHandler;

/*
 * TODO test doesn't assert anything
 */
public class MusicMountBuilderTest {
	@Rule
	public TemporaryFolder outputFolder = new TemporaryFolder();
	URL inputFolder = getClass().getResource("/sample-library");

	@Test
	public void test() throws Exception {
		ResourceProvider resourceProvider = new FileResourceProvider();
		Resource musicFolder = resourceProvider.newResource(new File(inputFolder.toURI()).toPath());
		Resource mountFolder = resourceProvider.newResource(outputFolder.getRoot().toPath());
		String musicPath = mountFolder.getPath().relativize(musicFolder.getPath()).toString();

		MusicMountBuilder builder = new MusicMountBuilder();
		builder.getConfig().setPretty(true);

		builder.getConfig().setFull(true);
		builder.build(musicFolder, mountFolder, musicPath);

		builder.getConfig().setFull(false); // use asset store
		builder.build(musicFolder, mountFolder, musicPath);
	}
	
	@Test
	public void testGenerateResponseFiles() throws Exception {
		ResourceProvider resourceProvider = new FileResourceProvider();
		Resource musicFolder = resourceProvider.newResource(new File(inputFolder.toURI()).toPath());
		Resource mountFolder = resourceProvider.newResource(outputFolder.getRoot().toPath());

		AssetStore assetStore = new AssetStore("test", musicFolder);
		assetStore.update(new SimpleAssetParser(), 4, ProgressHandler.NOOP);
		Library library = new LibraryParser(true).parse(assetStore.assets());

		new MusicMountBuilder().generateResponseFiles(library, musicFolder, mountFolder, "music");
	}
}
