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
import java.nio.file.Paths;
import java.text.Normalizer;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;

public class SimpleAssetLocatorTest {
	@Test
	@Ignore
	public void testReencodeURIPathNormalize() throws Exception {
		String path = "/Track/U%CC%88ber%20Den%20Da%CC%88chern%20Von%20Stuttgart%2F21.m4a";
		String normalizedPath = SimpleAssetLocator.encodeURIPath(path, Normalizer.Form.NFC);
		Assert.assertEquals("/Track/%C3%9Cber%20Den%20D%C3%A4chern%20Von%20Stuttgart%2F21.m4a", normalizedPath);
		Assert.assertEquals(path, SimpleAssetLocator.encodeURIPath(path, null));
	}

	@Test
	public void testEncodeURIPath() throws Exception {
		Assert.assertEquals("Me%20%2B%20You", SimpleAssetLocator.encodeURIPath("Me + You", null));
		Assert.assertEquals("Me%20%2B%20You/", SimpleAssetLocator.encodeURIPath("Me + You/", null));
		Assert.assertEquals("/Me%20%2B%20You", SimpleAssetLocator.encodeURIPath("/Me + You", null));
		Assert.assertEquals("/Me%20%2B%20You/", SimpleAssetLocator.encodeURIPath("/Me + You/", null));
		Assert.assertEquals("/Me%20%2B%20You/And%20Them", SimpleAssetLocator.encodeURIPath("/Me + You/And Them", null));
	}

	@Test
	@Ignore
	public void testPathNormalization() {
		// behavior changed from jdk7u25 to jdk7u45, seems to normalize to NFC by default!!!
		Assert.assertEquals("Bjo\u0308rk", Paths.get("Bjo\u0308rk").toString());
	}
	
	@Test
	public void testGetAssetPath() throws IOException, URISyntaxException {
		ResourceProvider resourceProvider = new FileResourceProvider();

		Resource baseFolder = resourceProvider.newResource(System.getProperty("user.home"));
		SimpleAssetLocator locator = new SimpleAssetLocator(baseFolder, "music", null);
		Assert.assertEquals("music/sample-aac.m4a", locator.getAssetPath(baseFolder.resolve("sample-aac.m4a")));
		Assert.assertEquals("music/sample%20aac.m4a", locator.getAssetPath(baseFolder.resolve("sample aac.m4a")));
		Assert.assertEquals("music/Bj%C3%B6rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(baseFolder.resolve("Bj\u00F6rk/Vespertine/07 Aurora.m4a")));
		// behavior changed from jdk7u25 to jdk7u45, seems to normalize to NFC by default!!!
//		Assert.assertEquals("music/Bjo%CC%88rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(baseFolder.resolve("Bjo\u0308rk/Vespertine/07 Aurora.m4a"))); // combining diaeresis

		locator = new SimpleAssetLocator(baseFolder, "music", Normalizer.Form.NFC); // perform character composition
		Assert.assertEquals("music/Bj%C3%B6rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(baseFolder.resolve("Bjo\u0308rk/Vespertine/07 Aurora.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "music", Normalizer.Form.NFD); // perform character decomposition
		Assert.assertEquals("music/Bjo%CC%88rk/Vespertine/07%20Aurora.m4a", locator.getAssetPath(baseFolder.resolve("Bj\u00F6rk/Vespertine/07 Aurora.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "///music///", null);
		Assert.assertEquals("/music/sample-aac.m4a", locator.getAssetPath(baseFolder.resolve("sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "", null);
		Assert.assertEquals("sample-aac.m4a", locator.getAssetPath(baseFolder.resolve("sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "music/sample-album", null);
		Assert.assertEquals("music/sample-album/sample-aac.m4a", locator.getAssetPath(baseFolder.resolve("sample-aac.m4a")));

		locator = new SimpleAssetLocator(baseFolder, "my music", null);
		Assert.assertEquals("my%20music/sample-aac.m4a", locator.getAssetPath(baseFolder.resolve("sample-aac.m4a")));
	}

	@Test
	public void testGetAssetFile() throws IOException, URISyntaxException {
		ResourceProvider resourceProvider = new FileResourceProvider();

		Resource assetFile = resourceProvider.newResource(new File(getClass().getResource("/sample-album/sample.mp3").toURI()).toPath());
		SimpleAssetLocator locator = new SimpleAssetLocator(assetFile.getParent(), "music", null);
		Assert.assertEquals(assetFile, locator.getAssetResource("music/sample.mp3"));
	}

}
