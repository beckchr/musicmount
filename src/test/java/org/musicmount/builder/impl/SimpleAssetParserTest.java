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

import java.io.File;
import java.util.Arrays;

import junit.framework.Assert;

import org.junit.Test;
import org.musicmount.builder.model.Track;

public class SimpleAssetParserTest {
	@Test
	public void testIsAssetFile() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser(Arrays.asList(".m4a", ".mp3"));
		Assert.assertTrue(assetParser.isAssetFile(new File("foo.mp3")));
		Assert.assertTrue(assetParser.isAssetFile(new File("foo.m4a")));
		Assert.assertFalse(assetParser.isAssetFile(new File("foo.txt")));
	}	
	
	@Test
	public void testParseAsset() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser();

		File mp3File = new File(getClass().getResource("/sample-assets/sample.mp3").toURI());
		Track mp3Track = assetParser.parse(mp3File);
		Assert.assertEquals("Sample MP3", mp3Track.getName());
		Assert.assertEquals("Sample Album", mp3Track.getAlbum());
		Assert.assertEquals("Sample Album Artist", mp3Track.getAlbumArtist());
		Assert.assertEquals("Sample Artist", mp3Track.getArtist());
		Assert.assertEquals(mp3File, mp3Track.getAssetFile());
		Assert.assertEquals("Sample Composer", mp3Track.getComposer());
		Assert.assertEquals("Sample Genre", mp3Track.getGenre());
		Assert.assertEquals(Integer.valueOf(1), mp3Track.getDiscNumber());
		Assert.assertEquals(Integer.valueOf(4), mp3Track.getDuration());
		Assert.assertEquals(Integer.valueOf(1), mp3Track.getTrackNumber());
		Assert.assertEquals(Integer.valueOf(2013), mp3Track.getYear());

		File m4aFile = new File(getClass().getResource("/sample-assets/sample.m4a").toURI());
		Track m4aTrack = assetParser.parse(m4aFile);
		Assert.assertEquals("Sample M4A", m4aTrack.getName());
		Assert.assertEquals("Sample Album", m4aTrack.getAlbum());
		Assert.assertEquals("Sample Album Artist", m4aTrack.getAlbumArtist());
		Assert.assertEquals("Sample Artist", m4aTrack.getArtist());
		Assert.assertEquals(m4aFile, m4aTrack.getAssetFile());
		Assert.assertEquals("Sample Composer", m4aTrack.getComposer());
		Assert.assertEquals("Sample Genre", m4aTrack.getGenre());
		Assert.assertEquals(Integer.valueOf(1), m4aTrack.getDiscNumber());
		Assert.assertEquals(Integer.valueOf(4), m4aTrack.getDuration());
		Assert.assertEquals(Integer.valueOf(1), m4aTrack.getTrackNumber());
		Assert.assertEquals(Integer.valueOf(2013), m4aTrack.getYear());
	}
	
	@Test
	public void testGetArtwork() throws Exception {
		SimpleAssetParser assetParser = new SimpleAssetParser();

		File mp3File = new File(getClass().getResource("/sample-assets/sample.mp3").toURI());
		Assert.assertNotNull(assetParser.extractArtwork(mp3File));
		
		File m4aFile = new File(getClass().getResource("/sample-assets/sample.m4a").toURI());
		Assert.assertNotNull(assetParser.extractArtwork(m4aFile));
	}
}
