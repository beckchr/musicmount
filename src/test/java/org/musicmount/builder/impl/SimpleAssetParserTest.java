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

import java.nio.file.Paths;

import junit.framework.Assert;

import org.junit.Test;
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;

public class SimpleAssetParserTest {
	ResourceProvider resourceProvider = new FileResourceProvider();

	@Test
	public void testIsAssetFile() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser();
		Assert.assertTrue(assetParser.isAssetPath(Paths.get("foo.mp3")));
		Assert.assertTrue(assetParser.isAssetPath(Paths.get("foo.m4a")));
		Assert.assertFalse(assetParser.isAssetPath(Paths.get("foo.txt")));
	}	
	
	@Test
	public void testParseAsset() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser();

		Resource mp3File = resourceProvider.newResource(Paths.get(getClass().getResource("/sample-assets/sample.mp3").toURI()));
		Asset mp3Asset = assetParser.parse(mp3File);
		Assert.assertEquals("Sample MP3", mp3Asset.getName());
		Assert.assertEquals("Sample Album", mp3Asset.getAlbum());
		Assert.assertEquals("Sample Album Artist", mp3Asset.getAlbumArtist());
		Assert.assertEquals("Sample Artist", mp3Asset.getArtist());
		Assert.assertEquals(mp3File, mp3Asset.getResource());
		Assert.assertEquals("Sample Composer", mp3Asset.getComposer());
		Assert.assertEquals("Sample Genre", mp3Asset.getGenre());
		Assert.assertEquals(Integer.valueOf(1), mp3Asset.getDiscNumber());
		Assert.assertEquals(Integer.valueOf(4), mp3Asset.getDuration());
		Assert.assertEquals(Integer.valueOf(1), mp3Asset.getTrackNumber());
		Assert.assertEquals(Integer.valueOf(2013), mp3Asset.getYear());

		Resource m4aFile = resourceProvider.newResource(Paths.get(getClass().getResource("/sample-assets/sample.m4a").toURI()));
		Asset m4aAsset = assetParser.parse(m4aFile);
		Assert.assertEquals("Sample M4A", m4aAsset.getName());
		Assert.assertEquals("Sample Album", m4aAsset.getAlbum());
		Assert.assertEquals("Sample Album Artist", m4aAsset.getAlbumArtist());
		Assert.assertEquals("Sample Artist", m4aAsset.getArtist());
		Assert.assertEquals(m4aFile, m4aAsset.getResource());
		Assert.assertEquals("Sample Composer", m4aAsset.getComposer());
		Assert.assertEquals("Sample Genre", m4aAsset.getGenre());
		Assert.assertEquals(Integer.valueOf(1), m4aAsset.getDiscNumber());
		Assert.assertEquals(Integer.valueOf(4), m4aAsset.getDuration());
		Assert.assertEquals(Integer.valueOf(1), m4aAsset.getTrackNumber());
		Assert.assertEquals(Integer.valueOf(2013), m4aAsset.getYear());
	}
	
	@Test
	public void testGetArtwork() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser();

		Resource mp3File = resourceProvider.newResource(Paths.get(getClass().getResource("/sample-assets/sample.mp3").toURI()));
		Assert.assertNotNull(assetParser.extractArtwork(mp3File));
		
		Resource m4aFile = resourceProvider.newResource(Paths.get(getClass().getResource("/sample-assets/sample.m4a").toURI()));
		Assert.assertNotNull(assetParser.extractArtwork(m4aFile));
	}
}
