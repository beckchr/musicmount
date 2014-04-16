package org.musicmount.server;

import org.junit.Assert;
import org.junit.Test;

public class MusicMountTestServerTest {
	MusicMountTestServer server = new MusicMountTestServer();

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
		Assert.assertEquals("/1/musicmount", server.mountContextPath("../../path"));
		Assert.assertEquals("/1/2/musicmount", server.mountContextPath("../../../path"));
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
}
