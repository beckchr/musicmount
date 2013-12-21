package org.musicmount.util.mp3;

import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;

public class ID3v2InfoTest {
	@Test
	public void testSample() throws Exception {
		try (MP3Input input = new MP3Input(getClass().getResource("/sample-assets/sample.mp3").openStream())) {
			ID3v2Info info = new ID3v2Info(input, Level.FINEST);

			// relevant fields
			Assert.assertEquals("Sample MP3", info.getTitle());
			Assert.assertEquals("Sample Artist", info.getArtist());
			Assert.assertEquals("Sample Album Artist", info.getAlbumArtist());
			Assert.assertEquals("Sample Album", info.getAlbum());
			Assert.assertEquals("Sample Genre", info.getGenre());
			Assert.assertEquals("Sample Composer", info.getComposer());
			Assert.assertEquals(0L, info.getDuration());
			Assert.assertFalse(info.isCompilation());
			Assert.assertEquals(2013, info.getYear());
			Assert.assertEquals(1, info.getTrack());
			Assert.assertEquals(1, info.getDisc());
			Assert.assertNotNull(info.getCover());
//
//			// other fields
			Assert.assertNull(info.getGrouping());
			Assert.assertNull(info.getComment());
			Assert.assertEquals(1, info.getTracks());
			Assert.assertEquals(1, info.getDiscs());
			Assert.assertEquals(0, info.getTempo());
			Assert.assertNull(info.getCopyright());
			Assert.assertEquals(0, info.getRating());
			Assert.assertNull(info.getLyrics());
		}
	}
}
