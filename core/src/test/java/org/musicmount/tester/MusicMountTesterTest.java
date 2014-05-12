package org.musicmount.tester;

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
}
