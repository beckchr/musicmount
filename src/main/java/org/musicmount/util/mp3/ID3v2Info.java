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
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ID3v2Info {
	static final Logger LOGGER = Logger.getLogger(ID3v2Info.class.getName());

	public static boolean isID3v2StartPosition(InputStream input) throws IOException {
		input.mark(3);
		try {
			return input.read() == 'I' && input.read() == 'D' && input.read() == '3';
		} finally {
			input.reset();
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
	private final byte[] textBuffer = new byte[1024];


	public ID3v2Info(InputStream input) throws IOException, ID3v2Exception {
		this(input, Level.FINEST);
	}

	public ID3v2Info(InputStream input, Level debugLevel) throws IOException, ID3v2Exception {
		this.debugLevel = debugLevel;
		if (isID3v2StartPosition(input)) {
			ID3v2TagHeader tagHeader = new  ID3v2TagHeader(input);
			version = String.format("2.%d.%d", tagHeader.getVersion(), tagHeader.getRevision());
			ID3v2TagBody tagBody = tagHeader.tagBody(input);
			try {
				while (tagBody.getRemainingLength() > 10) { // TODO > tag.minimumFrameSize()
					ID3v2FrameHeader frameHeader = new ID3v2FrameHeader(tagBody);
					if (frameHeader.isPadding()) { // we ran into padding
						break;
					}
					if (frameHeader.getBodySize() > tagBody.getRemainingLength()) { // something wrong...
						LOGGER.log(debugLevel, "ID3 frame claims to extend frames area");
						break;
					}
					if (frameHeader.isValid() && !frameHeader.isEncryption()) {
						ID3v2FrameBody frameBody = tagBody.frameBody(frameHeader);
						try {
							parseFrame(frameBody);
						} catch (ID3v2Exception e) {
							LOGGER.fine("ID3 exception occured: " + e.getMessage());
						} finally {
							frameBody.getData().skipFully(frameBody.getRemainingLength());
						}
					} else {
						tagBody.getData().skipFully(frameHeader.getBodySize());
					}
				}
			} catch (ID3v2Exception e) {
				LOGGER.fine("ID3 exception occured: " + e.getMessage());
			}
			tagBody.getData().skipFully(tagBody.getRemainingLength());
			if (tagHeader.getFooterSize() > 0) {
				input.skip(tagHeader.getFooterSize());
			}
		}
	}

	void parseFrame(ID3v2FrameBody frame) throws IOException, ID3v2Exception {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, "Parsing frame: " + frame.getFrameHeader().getFrameId());
		}
		switch (frame.getFrameHeader().getFrameId()) {
		case "PIC":
		case "APIC":
			cover = parsePictureFrame(frame);
			break;
		case "COM":
		case "COMM":
			comment = parseCommentOrLyricsFrame(frame);
			break;
		case "TAL":
		case "TALB":
			album = parseTextFrame(frame);
			break;
		case "TCP":
		case "TCMP":
			compilation = "1".equals(parseTextFrame(frame));
			break;
		case "TCM":
		case "TCOM":
			composer = parseTextFrame(frame);
			break;
		case "TCO":
		case "TCON":
			String tcon = parseTextFrame(frame);
			if (tcon.length() > 0) {
				genre = tcon;
				try {
					ID3v1Genre id3v1Genre = null;
					if (tcon.charAt(0) == '(') {
						int pos = tcon.indexOf(')');
						if (pos > 1) { // (123)
							id3v1Genre = ID3v1Genre.getGenre(Integer.parseInt(tcon.substring(1, pos)));
							if (id3v1Genre == null && tcon.length() > pos + 1) { // (789)Special
								genre = tcon.substring(pos + 1);
							}
						}
					} else { // 123
						id3v1Genre = ID3v1Genre.getGenre(Integer.parseInt(tcon));
					}
					if (id3v1Genre != null) {
						genre = id3v1Genre.getDescription();
					}
				} catch (NumberFormatException e) {
					// ignore
				}
			}
			break;
		case "TCR":
		case "TCOP":
			copyright = parseTextFrame(frame);
			break;
		case "TDRC": // v2.4, replaces TYER
			String tdrc = parseTextFrame(frame);
			if (tdrc.length() >= 4) {
				try {
					year = Short.valueOf(tdrc.substring(0, 4));
				} catch (NumberFormatException e) {
					if (LOGGER.isLoggable(debugLevel)) {
						LOGGER.log(debugLevel, "Could not parse year from: " + tdrc);
					}
				}
			}
			break;
		case "TLE":
		case "TLEN":
			String tlen = parseTextFrame(frame);
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
			artist = parseTextFrame(frame);
			break;
		case "TP2":
		case "TPE2":
			albumArtist = parseTextFrame(frame);
			break;
		case "TPA":
		case "TPOS":
			String tpos = parseTextFrame(frame);
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
			String trck = parseTextFrame(frame);
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
			grouping = parseTextFrame(frame);
			break;
		case "TT2":
		case "TIT2":
			title = parseTextFrame(frame);
			break;
		case "TYE":
		case "TYER":
			String tyer = parseTextFrame(frame);
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
		case "ULT":
		case "USLT":
			lyrics = parseCommentOrLyricsFrame(frame);
			break;
		default:
			frame.getData().skipFully(frame.getRemainingLength());
			break;
		}
	}

	String parseTextFrame(ID3v2FrameBody frame) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = frame.readEncoding();
		return frame.readFixedLengthString(textBuffer, (int)frame.getRemainingLength(), encoding); // text
	}
	
	String parseCommentOrLyricsFrame(ID3v2FrameBody data) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = data.readEncoding();
		data.readFixedLengthString(textBuffer, 3, encoding); // language
		data.readZeroTerminatedString(textBuffer, 200, encoding); // description
		return data.readFixedLengthString(textBuffer, (int)data.getRemainingLength(), encoding); // text
	}

	byte[] parsePictureFrame(ID3v2FrameBody data) throws IOException, ID3v2Exception {
		ID3v2Encoding encoding = data.readEncoding();
		if (data.getTagHeader().getVersion() == 2) { // file type, e.g. "JPG"
			data.readFixedLengthString(textBuffer, 3, ID3v2Encoding.ISO_8859_1);
		} else { // mime type, e.g. "image/jpg"
			data.readZeroTerminatedString(textBuffer, 20, ID3v2Encoding.ISO_8859_1);
		}
		data.getData().readByte(); // picture type
		data.readZeroTerminatedString(textBuffer, 200, encoding); // description
		return data.getData().readFully((int)data.getRemainingLength()); // image data
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
