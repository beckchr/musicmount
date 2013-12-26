package org.musicmount.util.mp3;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class ID3v1InfoTest {
	@Test
	public void testV1Tag() throws Exception {
		File mp3File = new File(getClass().getResource("/sample-assets/v1tag.mp3").toURI());
		try (MP3Input input = new MP3Input(mp3File.toURI().toURL().openStream())) {
			input.skip(mp3File.length() - 128);
			Assert.assertTrue(ID3v1Info.isID3v1StartPosition(input));

			ID3v1Info info = new ID3v1Info(input);

			// relevant fields
			Assert.assertEquals("TITLE1234567890123456789012345", info.getTitle());
			Assert.assertEquals("ARTIST123456789012345678901234", info.getArtist());
			Assert.assertEquals("ALBUM1234567890123456789012345", info.getAlbum());
			Assert.assertEquals(ID3v1Genre.Pop, info.getGenre());
			Assert.assertEquals(2001, info.getYear());
			Assert.assertEquals(1, info.getTrack());
			Assert.assertEquals("COMMENT123456789012345678901", info.getComment());
		}
	}
}
