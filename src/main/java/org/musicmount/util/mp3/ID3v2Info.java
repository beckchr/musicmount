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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mpatric.mp3agic.ID3v1Genres;

public class ID3v2Info {
	static final Logger LOGGER = Logger.getLogger(ID3v2Info.class.getName());

	public static boolean isID3v2StartPosition(MP3Input data) throws IOException {
		data.mark(3);
		try {
			return data.read() == 'I' && data.read() == 'D' && data.read() == '3';
		} finally {
			data.reset();
		}
	}
	
	private String version;		// e.g. 2.3.0
	private long duration;		// in milliseconds
	private String title;
	private String artist;
	private String albumArtist;
	private String album;
	private short year;
	private String genre;
	private String comment;
	private short track;
	private short tracks;
	private short disc;
	private short discs;
	private String copyright;
	private String composer;
	private short tempo;		// not supported
	private String grouping;
	private byte rating;		// not supported
	private boolean compilation;
	private String lyrics;
	private byte[] cover;

	private final Level debugLevel;

	public ID3v2Info(MP3Input data) throws IOException, ID3Exception {
		this(data, Level.FINEST);
	}

	public ID3v2Info(MP3Input data, Level debugLevel) throws IOException, ID3Exception {
		this.debugLevel = debugLevel;
		if (isID3v2StartPosition(data)) {
			ID3v2Header tag = new  ID3v2Header(data);
			version = String.format("2.%d.%d", tag.getVersion(), tag.getRevision());
			if (tag.isUnsynchronization() || tag.isCompression()) {
				throw new ID3Exception("Unsynchronization is not supported");
			}
			try {
				int remainingBodySize = tag.getBodySize();
				while (remainingBodySize > 0) { // TODO: remainingBodySize > tag.getMinimumFrameSize()
					long headerPosition = data.getPosition();
					ID3v2FrameHeader frame = new ID3v2FrameHeader(data, tag);
					remainingBodySize -= (data.getPosition() - headerPosition);
					if (frame.isPadding()) { // we ran into padding
						break;
					}
					if (frame.getBodySize() > remainingBodySize) { // something wrong...
						LOGGER.log(debugLevel, "ID3 frame claims to extend tag body");
						break;
					}
					if (!frame.isValid() || frame.isCompression() || frame.isEncryption() || frame.isUnsynchronization()) {
						data.skipFully(frame.getBodySize());
					} else {
						parseFrame(data, frame);
					}
					remainingBodySize -= frame.getBodySize();
				}
				if (remainingBodySize > 0) {
					data.skipFully(remainingBodySize + tag.getPaddingSize() + tag.getFooterSize());
				}
			} catch (ID3Exception e) {
				LOGGER.fine("ID3 exception occured: " + e.getMessage());
			}
		}
		// TODO id3v1
	}

	void parseFrame(MP3Input data, ID3v2FrameHeader frame) throws IOException, ID3Exception {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, "Parsing frame: " + frame.getFrameId());
		}
		switch (frame.getFrameId()) {
		case "PIC":
		case "APIC":
			cover = parsePictureFrame(data, frame); 
			break;
		case "COM":
		case "COMM":
			comment = parseCommentFrame(data, frame);
			break;
		case "TAL":
		case "TALB":
			album = parseTextFrame(data, frame);
			break;
		case "TCP":
		case "TCMP":
			compilation = "1".equals(parseTextFrame(data, frame));
			break;
		case "TCM":
		case "TCOM":
			composer = parseTextFrame(data, frame);
			break;
		case "TCO":
		case "TCON":
			String tcon = parseTextFrame(data, frame);
			try {
				int genreNumber = extractGenreNumber(tcon);
				if (genreNumber >= 0 && genreNumber < ID3v1Genres.GENRES.length) {
					genre = ID3v1Genres.GENRES[genreNumber];
				} else {
					genre = extractGenreDescription(tcon);
				}
			} catch (NumberFormatException e) {
				genre = extractGenreDescription(tcon);
			}
			break;
		case "TCR":
		case "TCOP":
			copyright = parseTextFrame(data, frame);
			break;
		case "TLE":
		case "TLEN":
			String tlen = parseTextFrame(data, frame);
			try {
				duration = Long.valueOf(tlen);
			} catch (NumberFormatException e) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, "Could not parse track duration: " + tlen);
				}
			}
			break;
		case "TP1":
		case "TPE1":
			artist = parseTextFrame(data, frame);
			break;
		case "TP2":
		case "TPE2":
			albumArtist = parseTextFrame(data, frame);
			break;
		case "TPA":
		case "TPOS":
			String tpos = parseTextFrame(data, frame);
			if (tpos.length() > 0) {
				int index = tpos.indexOf('/');
				if (index < 0) {
					try {
						disc = Short.valueOf(tpos);
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse disc number: " + tpos);
						}
					}
				} else {
					try {
						disc = Short.valueOf(tpos.substring(0, index));
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse disc number: " + tpos);
						}
					}
					try {
						discs = Short.valueOf(tpos.substring(index + 1));
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse number of discs: " + tpos);
						}
					}
				}
			}
			break;
		case "TRK":
		case "TRCK":
			String trck = parseTextFrame(data, frame);
			if (trck.length() > 0) {
				int index = trck.indexOf('/');
				if (index < 0) {
					try {
						track = Short.valueOf(trck);
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse track number: " + trck);
						}
					}
				} else {
					try {
						track = Short.valueOf(trck.substring(0, index));
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {							
							LOGGER.log(debugLevel, "Could not parse track number: " + trck);
						}
					}
					try {
						tracks = Short.valueOf(trck.substring(index + 1));
					} catch (NumberFormatException e) {
						if (LOGGER.isLoggable(debugLevel)) {
							LOGGER.log(debugLevel, "Could not parse number of tracks: " + trck);
						}
					}
				}
			}
			break;
		case "TT1":
		case "TIT1":
			grouping = parseTextFrame(data, frame);
			break;
		case "TT2":
		case "TIT2":
			title = parseTextFrame(data, frame);
			break;
		case "TYE":
		case "TYER":
			String tyer = parseTextFrame(data, frame);
			if (tyer.length() > 0) {
				try {
					year = Short.valueOf(tyer);
				} catch (NumberFormatException e) {
					if (LOGGER.isLoggable(debugLevel)) {
						LOGGER.log(debugLevel, "Could not parse year: " + tyer);
					}
				}
			}
			break;
		default:
			data.skipFully(frame.getBodySize());
			break;
		}
	}
	
	protected int extractGenreNumber(String tcon) throws NumberFormatException {
		if (tcon.length() > 0) {
			if (tcon.charAt(0) == '(') {
				int pos = tcon.indexOf(')');
				if (pos > 0) {
					return Integer.parseInt(tcon.substring(1, pos));
				}
			}
		}
		return Integer.parseInt(tcon);
	}
	
	protected String extractGenreDescription(String tcon) throws NumberFormatException {
		if (tcon.length() > 0) {
			if (tcon.charAt(0) == '(') {
				int pos = tcon.indexOf(')');
				if (pos > 0) {
					return tcon.substring(pos + 1);
				}
			}
		}
		return tcon;
	}
	
	String extractString(byte[] bytes, int offset, int length, ID3v2Encoding encoding) throws UnsupportedEncodingException {
		String text = new String(bytes, offset, length, encoding.getCharsetName());
		if (encoding.isBOM() && text.length() > 0 && text.charAt(0) == '\uFEFF') {
			text = text.substring(1);
		}
		int zeroIndex = text.indexOf(0);
		return zeroIndex < 0 ? text : text.substring(0, zeroIndex);
	}

	String extractString(byte[] bytes, ID3v2Encoding encoding) throws UnsupportedEncodingException {
		return extractString(bytes, 0, bytes.length, encoding);
	}

	String readString(MP3Input data, int len, ID3v2Encoding encoding) throws IOException {
		return extractString(data.readFully(len), encoding);
	}

	/**
	 * copy bytes of zero(s)-terminated string into buffer.
	 * @param data
	 * @param bytes
	 * @param encoding
	 * @return byte count (including terminating zero(s))
	 * @throws IOException
	 */
	int readZeroTerminatedString(MP3Input data, byte[] bytes, ID3v2Encoding encoding) throws IOException, ID3Exception {
		for (int i = 0; i < bytes.length; i++) {
			if ((bytes[i] = data.readByte()) == 0 && i >= encoding.getZeroBytes() - 1) {
				for (int j = 1; j < encoding.getZeroBytes(); j++) {
					if (bytes[i-j] != 0) {
						break;
					}
				}
				return i + 1;
			}
		}
		throw new ID3Exception("Could not read zero-termiated string");
	}

	String parseTextFrame(MP3Input data, ID3v2FrameHeader frame) throws IOException, ID3Exception {
		return readString(data, frame.getBodySize() - 1, ID3v2Encoding.getEncoding(data.readByte()));
	}
	
	String parseCommentFrame(MP3Input data, ID3v2FrameHeader frame) throws IOException, ID3Exception {
		ID3v2Encoding encoding = ID3v2Encoding.getEncoding(data.readByte());
		byte[] textBuffer = new byte[Math.min(frame.getBodySize(), 256)];

		int languageByteCount = 3;
		data.readFully(textBuffer, 0, languageByteCount);
//		extractString(textBuffer, 0, languageByteCount, ID3v2Encoding.ISO_8859_1);
		
		int descriptionByteCount = readZeroTerminatedString(data, textBuffer, encoding);
//		extractString(textBuffer, 0, descriptionByteCount, encoding);

		return readString(data, frame.getBodySize() - (1 + languageByteCount + descriptionByteCount), encoding);
	}

	byte[] parsePictureFrame(MP3Input data, ID3v2FrameHeader frame) throws IOException, ID3Exception {
		ID3v2Encoding encoding = ID3v2Encoding.getEncoding(data.readByte());
		byte[] textBuffer = new byte[Math.min(frame.getBodySize(), 256)];

		int imageTypeByteCount;
		if (frame.getTag().getVersion() == 2) {
			imageTypeByteCount = 3;
			data.readFully(textBuffer, 0, imageTypeByteCount);
		} else {
			imageTypeByteCount = readZeroTerminatedString(data, textBuffer, ID3v2Encoding.ISO_8859_1);
		}
//		extractString(textBuffer, 0, imageTypeByteCount, ID3v2Encoding.ISO_8859_1)

		data.readByte(); // picture type

		int descriptionByteCount = readZeroTerminatedString(data, textBuffer, encoding);
		extractString(textBuffer, 0, descriptionByteCount, encoding);
		
		return data.readFully(frame.getBodySize() - (1 + imageTypeByteCount + 1 + descriptionByteCount));
	}
	
	public String getBrand() {
		return "ID3";
	}
	
	public String getVersion() {
		return version;
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

	public String getAlbumArtist() {
		return albumArtist;
	}

	public short getYear() {
		return year;
	}

	public String getComment()  {
		return comment;
	}

	public short getTrack() {
		return track;
	}
	
	public short getTracks() {
		return tracks;
	}

	public short getDisc() {
		return disc;
	}
	
	public short getDiscs() {
		return discs;
	}

	public String getGenre() {
		return genre;
	}

	public String getGrouping() {
		return grouping;
	}

	public String getCopyright() {
		return copyright;
	}

	public String getComposer() {
		return composer;
	}

	public short getTempo() {
		return tempo;
	}

	public long getDuration() {
		return duration;
	}

	public boolean isCompilation() {
		return compilation;
	}

	public String getLyrics() {
		return lyrics;
	}

	public byte getRating() {
		return rating;
	}
	
	public byte[] getCover() {
		return cover;
	}
}