package org.musicmount.tester;

import java.net.MalformedURLException;

import org.junit.Assert;
import org.junit.Test;
import org.musicmount.tester.MusicMountTester;

public class MusicMountTesterTest {
	MusicMountTester server = new MusicMountTester();

	@Test
	public void testNormalizeMusicPath() {
		Assert.assertEquals("path", server.normalizeMusicPath("path"));
		Assert.assertEquals("/path", server.normalizeMusicPath("/path"));
		Assert.assertEquals("path", server.normalizeMusicPath("path/"));
		Assert.assertEquals("path", server.normalizeMusicPath("./path"));
		Assert.assertEquals("path", server.normalizeMusicPath("path/."));
		Assert.assertEquals("../path", server.normalizeMusicPath("../path"));
	}

	@Test
	public void testMountContextPath() {
		Assert.assertEquals("/musicmount", server.mountContextPath("/path"));
		Assert.assertEquals("/musicmount", server.mountContextPath("path"));
		Assert.assertEquals("/musicmount", server.mountContextPath("../path"));
		Assert.assertEquals("/musicmount/2", server.mountContextPath("../../path"));
		Assert.assertEquals("/musicmount/2/3", server.mountContextPath("../../../path"));
		Assert.assertEquals("/musicmount", server.mountContextPath("/path/"));
	}

	@Test
	public void testMusicContextPath() {
		Assert.assertEquals("/path", server.musicContextPath("/path"));
		Assert.assertEquals("/musicmount/path", server.musicContextPath("path"));
		Assert.assertEquals("/path", server.musicContextPath("../path"));
		Assert.assertEquals("/path", server.musicContextPath("../../path"));
		Assert.assertEquals("/path", server.musicContextPath("/path/"));
	}

	@Test
	public void testGetSiteURL() throws MalformedURLException {
		Assert.assertEquals("http://<hostName>:1234/musicmount/index.json", server.getSiteURL("<hostName>", 1234, "path").toString());
	}
}
