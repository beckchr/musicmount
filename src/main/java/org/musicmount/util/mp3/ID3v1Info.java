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
package org.musicmount.util.mp3;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class ID3v1Info {
	public static boolean isID3v1StartPosition(MP3Input data) throws IOException {
		data.mark(3);
		try {
			return data.read() == 'T' && data.read() == 'A' && data.read() == 'G';
		} finally {
			data.reset();
		}
	}

	private String title;
	private String artist;
	private String album;
	private short year;
	private String comment;
	private short track;
	private ID3v1Genre genre;

	public ID3v1Info(MP3Input data) throws IOException {
		if (isID3v1StartPosition(data)) {
			byte[] bytes = data.readFully(128);
			title = extractString(bytes, 3, 30);
			artist = extractString(bytes, 33, 30);
			album = extractString(bytes, 63, 30);
			try {
				year = Short.parseShort(extractString(bytes, 93, 4));
			} catch (NumberFormatException e) {
				year = 0;
			}
			comment = extractString(bytes, 97, 30);
			genre = ID3v1Genre.getGenre(bytes[127]);
			
			/*
			 * ID3v1.1
			 */
			if (bytes[125] == 0 && bytes[126] != 0) {
				track = (short)(bytes[126] & 0xFF);
			}
 		}
	}

	String extractString(byte[] bytes, int offset, int length) throws UnsupportedEncodingException {
		String text = new String(bytes, offset, length, "ISO-8859-1");
		int zeroIndex = text.indexOf(0);
		return zeroIndex < 0 ? text : text.substring(0, zeroIndex);
	}

	public String getTitle() {
		return title;
	}

	public String getArtist() {
		return artist;
	}

	public String getAlbum() {
		return album;
	}

	public short getYear() {
		return year;
	}

	public String getComment() {
		return comment;
	}

	public short getTrack() {
		return track;
	}

	public ID3v1Genre getGenre() {
		return genre;
	}
}
