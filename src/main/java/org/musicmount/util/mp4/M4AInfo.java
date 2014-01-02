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
package org.musicmount.util.mp4;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.util.mp3.ID3v1Genre;

/**
 * M4A info.
 * 
 * We examine the following atom structure:
 *
 * [ftyp]                 - brand name (should be 'MP4A') and version
 * [moov]
 *   [mvhd]               - duration, speed, volume
 *   [trak]
 *     [mdia]
 *       [mdhd]           - sample rate, duration
 *   [udta]
 *      [meta]
 *         [ilst]
 *           [©nam]         title
 *           [©ART]         artist
 *           [aART]         album artist
 *           [©alb]         album
 *           [©day]         year
 *           [©cmt]         comment
 *           [gnre, ©gen]   genre (standard or custom, not both)
 *           [trkn]         track number
 *           [disk]         disk number
 *           [©wrt, ©com]   composer (iTunes seems to use only ©wrt)
 *           [tmpo]         BPM
 *           [cprt, ©cpy]   copyright (occurrence of ©cpy is unconfirmed)
 *           [cpil]         compilation
 *           [covr]         cover
 *           [rtng]         rating
 *           [©grp]         grouping (also [grup]?)
 *           [©lyr]         lyrics
 *
 * Further iTunes atoms which are not examined:
 *           [----]         ???
 *           [pgap]         gapless playback
 *           [apID]         apple store account
 *           [©enc]         encoded by
 *           [©too]         tool
 *           [desc, ©des]   description (also [dscp]?)
 *           [ldes]         long description
 *           [stik]         media type (0-Movie, 1-Music, 2-Audiobook, 5-Whacked Bookmark, 6-Music Video, 9-Short Film, 10-TV Show, 11-Booklet, 14-Ringtone, 21-Podcast)
 *           [catg]         category
 *           [keyw]         keyword
 *           [pcst]         podcast flag
 *           [purl]         podcast url
 *           [egid]         episode global unique id
 *           [tvnn]         TV network name
 *           [tvsh]         TV show name
 *           [tven]         TV episode number
 *           [tvsn]         TV season
 *           [tves]         TV episode
 *           [hdvd]         HD video flag
 *           [itnu]         iTunesU flag
 *           [purd]         purchase date
 *           [auth]         author
 *           [perf]         performer
 *           [titl]         title
 *           [yrrc]         year (of recording?)
 *           [akID]         iTunes store account type (0-iTunes, 1-AOL)
 *           [atID]         album title id
 *           [cnID]         apple store catalog id
 *           [geID]         genre id
 *           [plID]         playlist id
 *           [sfID]         iTunes store country code (143441-USA, 143442-France, 143443-Germany, 143444-UK, 143445-Austria, 143446-Belgium, 143447-Finland, 143448-Greece, 143449-Ireland, 143450-Italy, 143451-Luxembourg, 143452-Netherlands, 143453-Portugal, 143454-Spain, 143455-Canada, 143456-Sweden, 143457-Norway, 143458-Denmark, 143459-Switzerland, 143460-Australia, 143461-New Zealand, 143462-Japan)
 *           [soaa]         sort album artist
 *           [soal]         sort album
 *           [soar]         sort artist
 *           [soco]         sort composer
 *           [sonm]         sort name
 *           [sosn]         sort show
 * 
 */
public class M4AInfo {
	static final Logger LOGGER = Logger.getLogger(M4AInfo.class.getName());

	private static final String ASCII = "ISO8859_1";
	private static final String UTF_8 = "UTF-8";

	private String brand;			// "M4A", ...
	private String version;		// version info

	private long duration;		// in milliseconds
	private BigDecimal volume;		// normal = 1.0
	private BigDecimal speed;		// normal = 1.0

	private String title;
	private String artist;
	private String albumArtist;	// album artist
	private String album;
	private short year;
	private String genre;
	private String comment;
	private short track;
	private short tracks;			// total number of tracks
	private short disc;
	private short discs;			// total number of discs
	private String copyright;
	private String composer;
	private short tempo;
	private String grouping;
	private byte rating;			// none = 0, clean = 2, explicit = 4
	private boolean compilation;	// compilation flag
	private String lyrics;
	private byte[] cover;

	private final Level debugLevel;

	public M4AInfo(InputStream input) throws IOException {
		this(input, Level.FINEST);
	}

	public M4AInfo(InputStream input, Level debugLevel) throws IOException {
		this.debugLevel = debugLevel;
		MP4Input mp4 = new MP4Input(input);
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, mp4.toString());
		}
		ftyp(mp4.nextChild("ftyp"));
		moov(mp4.nextChildUpTo("moov"));
	}

	void ftyp(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		brand = atom.readString(4, ASCII).trim();
		if (brand.matches("M4V|MP4|mp42|isom")) { // experimental file types
			LOGGER.warning(atom.getPath() + ": brand=" + brand + " (experimental)");
		} else if (!brand.matches("M4A|M4P")) {
			LOGGER.warning(atom.getPath() + ": brand=" + brand + " (expected M4A or M4P)");
		}
		version = String.valueOf(atom.readInt());
	}

	void moov(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			switch (child.getType()) {
			case "mvhd":
				mvhd(child);
				break;
			case "trak":
				trak(child);
				break;
			case "udta":
				udta(child);
				break;
			default:
				break;
			}
		}
	}

	void mvhd(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		byte version = atom.readByte();
		atom.skip(3); // flags
		atom.skip(version == 1 ? 16 : 8); // created/modified date
		int scale = atom.readInt();
		long units = version == 1 ? atom.readLong() : atom.readInt();
		if (duration == 0) {
			duration = 1000 * units / scale;
		} else if (LOGGER.isLoggable(debugLevel) && Math.abs(duration - 1000 * units / scale) > 2) {
			LOGGER.log(debugLevel, "mvhd: duration " + duration + " -> " + (1000 * units / scale));
		}
		speed = atom.readIntegerFixedPoint();
		volume = atom.readShortFixedPoint();
	}

	void trak(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		mdia(atom.nextChildUpTo("mdia"));
	}

	void mdia(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		mdhd(atom.nextChild("mdhd"));
	}

	void mdhd(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		byte version = atom.readByte();
		atom.skip(3);
		atom.skip(version == 1 ? 16 : 8); // created/modified date
		int sampleRate = atom.readInt();
		long samples = version == 1 ? atom.readLong() : atom.readInt();
		if (duration == 0) {
			duration = 1000 * samples / sampleRate;
		} else if (LOGGER.isLoggable(debugLevel) && Math.abs(duration - 1000 * samples / sampleRate) > 2) {
			LOGGER.log(debugLevel, "mdhd: duration " + duration + " -> " + (1000 * samples / sampleRate));
		}
	}

	void udta(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if ("meta".equals(child.getType())) {
				meta(child);
				break;
			}
		}
	}

	void meta(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		atom.skip(4); // version/flags
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if ("ilst".equals(child.getType())) {
				ilst(child);
				break;
			}
		}
	}

	void ilst(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		while (atom.hasMoreChildren()) {
			MP4Atom child = atom.nextChild();
			if (LOGGER.isLoggable(debugLevel)) {
				LOGGER.log(debugLevel, child.toString());
			}
			if (child.getRemaining() == 0) {
				if (LOGGER.isLoggable(debugLevel)) {
					LOGGER.log(debugLevel, child.getPath() + ": contains no value");
				}
				continue;
			}
			data(child.nextChildUpTo("data"));
		}
	}
	
	void data(MP4Atom atom) throws IOException {
		if (LOGGER.isLoggable(debugLevel)) {
			LOGGER.log(debugLevel, atom.toString());
		}
		atom.skip(4); // version & flags
		atom.skip(4); // reserved
		switch (atom.getParent().getType()) {
		case "©alb":
			album = atom.readString(UTF_8);
			break;
		case "aART":
			albumArtist = atom.readString(UTF_8);
			break;
		case "©ART":
			artist = atom.readString(UTF_8);
			break;
		case "©cmt":
			comment = atom.readString(UTF_8);
			break;
		case "©com":
		case "©wrt":
			if (composer == null || composer.trim().length() == 0) {
				composer = atom.readString(UTF_8);
			}
			break;
		case "covr":
			cover = atom.readBytes();
			break;
		case "cpil":
			compilation = atom.readBoolean();
			break;
		case "cprt":
		case "©cpy":
			if (copyright == null || copyright.trim().length() == 0) {
				copyright = atom.readString(UTF_8);
			}
			break;
		case "©day":
			year = Short.valueOf(atom.readString(4, UTF_8)).shortValue();
			break;
		case "disk":
			atom.skip(2); // padding?
			disc = atom.readShort();
			discs = atom.readShort();
			break;
		case "gnre":
			if (genre == null || genre.trim().length() == 0) {
				if (atom.getRemaining() == 2) { // id3v1 genre?
					int index = atom.readShort() - 1;
					ID3v1Genre id3v1Genre = ID3v1Genre.getGenre(index);
					if (id3v1Genre != null) {
						genre = id3v1Genre.getDescription();
					}
				} else {
					genre = atom.readString(UTF_8);
				}
			}
			break;
		case "©gen":
			if (genre == null || genre.trim().length() == 0) {
				genre = atom.readString(UTF_8);
			}
			break;
		case "©grp":
			grouping = atom.readString(UTF_8);
			break;
		case "©lyr":
			lyrics = atom.readString(UTF_8);
			break;
		case "©nam":
			title = atom.readString(UTF_8);
			break;
		case "rtng":
			rating = atom.readByte();
			break;
		case "tmpo":
			tempo = atom.readShort();
			break;
		case "trkn":
			atom.skip(2); // padding?
			track = atom.readShort();
			tracks = atom.readShort();
			break;
		default:
			break;
		}
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

	public String getBrand() {
		return brand;
	}

	public long getDuration() {
		return duration;
	}

	public String getVersion() {
		return version;
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

	public BigDecimal getSpeed() {
		return speed;
	}

	public BigDecimal getVolume() {
		return volume;
	}
	
	public byte[] getCover() {
		return cover;
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage: M4Info <file>");
			System.exit(1);
		}
		M4AInfo info = new M4AInfo(new FileInputStream(new File(args[0])));
		System.out.println("Audio info for " + args[0] + " (null or 0 means not available)");
		System.out.println("  Brand:        " + info.brand);
		System.out.println("  Version:      " + info.version);
		System.out.println("  Duration:     " + info.duration);
		System.out.println("  Grouping:     " + info.grouping);
		System.out.println("  Title:        " + info.title);
		System.out.println("  Artist:       " + info.artist);
		System.out.println("  Album artist: " + info.albumArtist);
		System.out.println("  Album:        " + info.album);
		System.out.println("  Year:         " + info.year);
		System.out.println("  Genre:        " + info.genre);
		System.out.println("  Composer:     " + info.composer);
		System.out.println("  Comment:      " + info.comment);
		System.out.println("  Track:        " + info.track + " of " + info.tracks);
		System.out.println("  Disc:         " + info.disc +  " of " + info.discs);
		System.out.println("  Tempo:        " + info.tempo);
		System.out.println("  Copyright:    " + info.copyright);
		System.out.println("  Speed:        " + info.speed);
		System.out.println("  Volume:       " + info.volume);
		System.out.println("  Compilation:  " + info.compilation);
		System.out.println("  Rating:       " + info.rating);
		System.out.println("  Lyrics:       " + info.lyrics);
	}
}
