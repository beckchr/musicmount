package org.musicmount.audio.m4a;

import java.io.InputStream;
import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;
import org.musicmount.audio.m4a.M4AInfo;

public class M4AInfoTest {
	@Test
	public void testSample() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/sample-assets/sample.m4a")) {
			M4AInfo info = new M4AInfo(input);

			// relevant fields
			Assert.assertEquals("Sample M4A", info.getTitle());
			Assert.assertEquals("Sample Artist", info.getArtist());
			Assert.assertEquals("Sample Album Artist", info.getAlbumArtist());
			Assert.assertEquals("Sample Album", info.getAlbum());
			Assert.assertEquals("Sample Genre", info.getGenre());
			Assert.assertEquals("Sample Composer", info.getComposer());
			Assert.assertEquals(4435L, info.getDuration());
			Assert.assertFalse(info.isCompilation());
			Assert.assertEquals(2013, info.getYear());
			Assert.assertEquals(1, info.getTrack());
			Assert.assertEquals(1, info.getDisc());
			Assert.assertNotNull(info.getCover());

			// other fields
			Assert.assertEquals("M4A", info.getBrand());
			Assert.assertEquals("0", info.getVersion());
			Assert.assertEquals(1, info.getTracks());
			Assert.assertEquals(1, info.getDiscs());
			Assert.assertNull(info.getGrouping());
			Assert.assertNull(info.getComment());
			Assert.assertEquals(0, info.getTempo());
			Assert.assertNull(info.getCopyright());
			Assert.assertEquals(BigDecimal.valueOf(1.0), info.getSpeed());
			Assert.assertEquals(BigDecimal.valueOf(1.0), info.getVolume());
			Assert.assertEquals(0, info.getRating());
			Assert.assertNull(info.getLyrics());
		}
	}
}
