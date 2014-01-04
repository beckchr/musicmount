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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.Normalizer;

import org.junit.Assert;
import org.junit.Test;

public class SimpleAssetLocatorTest {
	@Test
	public void testReencodeURIPathNormalize() throws Exception {
		String path = "/Track/U%CC%88ber%20Den%20Da%CC%88chern%20Von%20Stuttgart%2F21.m4a";
		String normalizedPath = SimpleAssetLocator.reencodeURIPath(path, Normalizer.Form.NFC);
		Assert.assertEquals("/Track/%C3%9Cber%20Den%20D%C3%A4chern%20Von%20Stuttgart%2F21.m4a", normalizedPath);
		Assert.assertEquals(path, SimpleAssetLocator.reencodeURIPath(path, null));
	}

	@Test
	public void testReencodeURIPath() throws Exception {
		Assert.assertEquals("Me%20%2B%20You", SimpleAssetLocator.reencodeURIPath("Me%20%2B%20You", null)); // Me + You
		Assert.assertEquals("Me%20%2B%20You/", SimpleAssetLocator.reencodeURIPath("Me%20%2B%20You/", null)); // Me + You
		Assert.assertEquals("/Me%20%2B%20You", SimpleAssetLocator.reencodeURIPath("/Me%20%2B%20You", null)); // Me + You
		Assert.assertEquals("/Me%20%2B%20You/", SimpleAssetLocator.reencodeURIPath("/Me%20%2B%20You/", null)); // Me + You
	}
	
	@Test
	public void testGetAssetPath() throws IOException, URISyntaxException {
		File baseFolder = new File(System.getProperty("user.home"));
		SimpleAssetLocator locator = new SimpleAssetLocator(baseFolder, "music", null);
		Assert.assertEquals("music/sample-aac.m4a", locator.getAssetPath(new File(baseFolder, "sample-aac.m4a")));
		Assert.assertEquals("music/sample%20aac.m4a", locator.getAssetPath(new File(baseFolder, "sample aac.m4a")));
		Assert.assertEquals("music/Bj%C3%B6rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(new File(baseFolder, "Bj\u00F6rk/Vespertine/07 Aurora.m4a")));
		Assert.assertEquals("music/Bjo%CC%88rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(new File(baseFolder, "Bjo\u0308rk/Vespertine/07 Aurora.m4a"))); // combining diaeresis

		locator = new SimpleAssetLocator(baseFolder, "music", Normalizer.Form.NFC); // perform character composition
		Assert.assertEquals("music/Bj%C3%B6rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(new File(baseFolder, "Bjo\u0308rk/Vespertine/07 Aurora.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "music", Normalizer.Form.NFD); // perform character decomposition
		Assert.assertEquals("music/Bjo%CC%88rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(new File(baseFolder, "Bj\u00F6rk/Vespertine/07 Aurora.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "///music///", null);
		Assert.assertEquals("/music/sample-aac.m4a", locator.getAssetPath(new File(baseFolder, "sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "", null);
		Assert.assertEquals("sample-aac.m4a", locator.getAssetPath(new File(baseFolder, "sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "music/sample-album", null);
		Assert.assertEquals("music/sample-album/sample-aac.m4a", locator.getAssetPath(new File(baseFolder, "sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "my music", null);
		Assert.assertEquals("my%20music/sample-aac.m4a", locator.getAssetPath(new File(baseFolder, "sample-aac.m4a")));
	}

	@Test
	public void testGetAssetFile() throws IOException, URISyntaxException {
		File assetFile = new File(getClass().getResource("/sample-album/sample.mp3").toURI());
		SimpleAssetLocator locator = new SimpleAssetLocator(assetFile.getParentFile(), "music", null);
		Assert.assertEquals(assetFile, locator.getAssetFile("music/sample.mp3"));
	}

}
