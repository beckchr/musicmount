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

import java.io.EOFException;
import java.io.IOException;

public class MP3Frame {

	public static final int MPEG_LAYER_1 = 3;
	public static final int MPEG_LAYER_2 = 2;
	public static final int MPEG_LAYER_3 = 1;
	public static final int MPEG_LAYER_RESERVED = 0;

	public static final int MPEG_VERSION_1   = 3;
	public static final int MPEG_VERSION_2   = 2;
	public static final int MPEG_VERSION_2_5 = 0;
	public static final int MPEG_VERSION_RESERVED = 1;

	public static final int MPEG_BITRATE_FREE = 0;
	public static final int MPEG_BITRATE_RESERVED = 15;
	public static final int MPEG_FRQUENCY_RESERVED = 3;

	public static final int MPEG_CHANNEL_MODE_MONO = 3;

	// [frequency][version]
	private static final int[][] FREQUENCIES = new int[][] {
			// 2.5   reserved   2       1
			{ 11025,    -1,  22050,  44100 },
			{ 12000,    -1,  24000,  48000 },
			{  8000,    -1,  16000,  32000 },
			{    -1,    -1,     -1,     -1 } // reserved
	};

	// [bitrate][version,layer]
	private static final int[][] BITRATES = new int[][] {
			{      0,      0,      0,      0,      0 }, // free
			{  32000,  32000,  32000,  32000,   8000 },
			{  64000,  48000,  40000,  48000,  16000 },
			{  96000,  56000,  48000,  56000,  24000 },
			{ 128000,  64000,  56000,  64000,  32000 },
			{ 160000,  80000,  64000,  80000,  40000 },
			{ 192000,  96000,  80000,  96000,  48000 },
			{ 224000, 112000,  96000, 112000,  56000 },
			{ 256000, 128000, 112000, 128000,  64000 },
			{ 288000, 160000, 128000, 144000,  80000 },
			{ 320000, 192000, 160000, 160000,  96000 },
			{ 352000, 224000, 192000, 176000, 112000 },
			{ 384000, 256000, 224000, 192000, 128000 },
			{ 416000, 320000, 256000, 224000, 144000 },
			{ 448000, 384000, 320000, 256000, 160000 },
			{     -1,     -1,     -1,     -1,     -1 } // reserved
	};

	// [version][layer]
	private static final int[][] BITRATES_COLUMN = new int[][] {
			// reserved  III        II         I
			{  -1,        4,         4,        3 }, // 2.5
			{  -1,       -1,        -1,       -1 }, // reserved
			{  -1,        4,         4,        3 }, // 2
			{  -1,        2,         1,        0 }  // 1
	};
	
	// [channelMode][version]
	private static final int[][] XING_HEADER_OFFSETS = new int[][] {
			// 2.5  reserved  2        1
			{  21,    -1,    21,      36 }, // stereo
			{  21,    -1,    21,      36 }, // joint stereo
			{  21,    -1,    21,      36 }, // dual channel
			{  13,    -1,    13,      21 }, // mono
	};
	
	// [version][layer]
	private static final int[][] SIZE_COEFFICIENTS = new int[][] {
			// reserved III        II         I
			{ -1,       72,       144,       12 }, // 2.5
			{ -1,       -1,        -1,       -1 }, // reserved
			{ -1,       72,       144,       12 }, // 2
			{ -1,      144,       144,       12 }  // 1
	};

	// [layer]
	private static final int[] SLOT_SIZES = new int[] {
		// reserved III        II         I
		  -1,        1,         1,        4
	};
	
	public static interface StopReadCondition {
		public static final StopReadCondition NEVER_STOP = new StopReadCondition() {
			@Override
			public boolean stopRead(MP3Input data) {
				return false;
			}
		};
		public boolean stopRead(MP3Input data) throws IOException;
	}

	public static MP3Frame readNextFrame(MP3Input data, StopReadCondition stopCondition) throws IOException {
		int b0 = 0;
		int b1;
		
		while (!stopCondition.stopRead(data)) {
			b1 = data.read();
			if (b1 == -1) {
				break;
			}
			if (b0 == 0xFF && (b1 & 0xE0) == 0xE0) { // first 11 bits should be 1
				data.mark(2);
				int b2 = data.read();
				if (b2 == -1) {
					break;
				}
				int b3 = data.read();
				if (b3 == -1) {
					break;
				}
				try {
					return new MP3Frame(b1, b2, b3, data);
				} catch (MP3Exception e) {
					data.reset();
				} catch (EOFException e) {
					break;
				}
			}
			b0 = b1;
		}
		return null;
	}
	
	public static long calculateDuration(MP3Input data, long totalLength, StopReadCondition stopCondition) throws IOException, MP3Exception {
		MP3Frame firstFrame = MP3Frame.readNextFrame(data, stopCondition);
		if (firstFrame != null) {
			// check for Xing header
			int numberOfFrames = firstFrame.getNumberOfFrames();
			if (numberOfFrames > 0) { // from Xing/VBRI header
				return firstFrame.getTotalDuration(numberOfFrames * firstFrame.getSize());
			} else { // scan file
				numberOfFrames = 1;

				long firstFramePosition = data.getPosition() - firstFrame.getSize();
				long frameSizeSum = firstFrame.getSize();

				int firstFrameBitrate = firstFrame.getBitrate();
				long bitrateSum = firstFrameBitrate;
				boolean vbr = false;
				int cbrThreshold = 10000 / firstFrame.getDuration(); // assume cbr after 10 seconds

				MP3Frame nextFrame = null;
				while (true) {
					if (numberOfFrames == cbrThreshold && !vbr && totalLength > 0) {
						return firstFrame.getTotalDuration(totalLength - firstFramePosition);
					}
					if ((nextFrame = MP3Frame.readNextFrame(data, stopCondition)) == null) {
						break;
					}
					if (!firstFrame.isCompatible(nextFrame)) {
						throw new MP3Exception("Incompatible frame");
					}
					int bitrate = nextFrame.getBitrate();
					if (bitrate != firstFrameBitrate) {
						vbr = true;
					}
					bitrateSum += bitrate;
					frameSizeSum += nextFrame.getSize();
					numberOfFrames++;
				}
				long duration = 1000L * frameSizeSum * numberOfFrames * 8 / bitrateSum; // == 1000 * frameSizeSum / (8 * averageBitrate)
				return duration;
			}
		} else {
			throw new MP3Exception("No audio frame");
		}
	}

	private final int version;
	private final int layer;
	private final int frequency;
	private final int bitrate;
	private final int channelMode;
	private final int padding;

	private final byte[] bytes;

	MP3Frame(int b1, int b2, int b3, MP3Input data) throws IOException, MP3Exception {
		version = b1 >> 3 & 0x3;
		if (version == MPEG_VERSION_RESERVED) {
			throw new MP3Exception("Reserved version");
		}
		layer = b1 >> 1 & 0x3;
		if (layer == MPEG_LAYER_RESERVED) {
			throw new MP3Exception("Reserved layer");
		}
		bitrate = b2 >> 4 & 0xF;
		if (bitrate == MPEG_BITRATE_RESERVED) {
			throw new MP3Exception("Reserved bitrate");
		}
		if (bitrate == MPEG_BITRATE_FREE) {
			throw new MP3Exception("Free bitrate");
		}
		frequency = b2 >> 2 & 0x3;
		if (frequency == MPEG_FRQUENCY_RESERVED) {
			throw new MP3Exception("Reserved frequency");
		}
		channelMode = b3 >> 6 & 0x3;
		padding = b2 >> 1 & 0x1;

		int size = ((SIZE_COEFFICIENTS[version][layer] * getBitrate() / getFrequency()) + padding) * SLOT_SIZES[layer];
		if (size < 4) {
			throw new MP3Exception("Size must be at least four");
		}
		bytes = new byte[size];
		bytes[0] = (byte) 0xff;
		bytes[1] = (byte) b1;
		bytes[2] = (byte) b2;
		bytes[3] = (byte) b3;
		data.readFully(bytes, 4, size - 4);
	}
	
	public int getVersion() {
		return version;
	}
	
	public int getLayer() {
		return layer;
	}
	
	public int getFrequency() {
		return FREQUENCIES[frequency][version];
	}

	public int getChannelMode() {
		return channelMode;
	}

	public int getSampleCount() {
		if (layer == MPEG_LAYER_1) {
			return 384;
		} else { // TODO correct?
			return 1152;
		}
	}

	public int getSize() {
		return bytes.length;
	}
	
	public int getBitrate() {
		return BITRATES[bitrate][BITRATES_COLUMN[version][layer]];
	}
	
	public int getDuration() {
		return (int)getTotalDuration(getSize());
	}

	public long getTotalDuration(long totalSize) {
		long duration = 1000L * (getSampleCount() * totalSize) / (getSize() * getFrequency());
		if (getVersion() != MP3Frame.MPEG_VERSION_1 && getChannelMode() == MP3Frame.MPEG_CHANNEL_MODE_MONO) {
			duration /= 2;
		}
		return duration;
	}

	boolean isXingFrame() {
		int xingOffset = XING_HEADER_OFFSETS[channelMode][version];
		if (bytes.length < xingOffset + 12) { // minimum Xing header size == 12
			return false;
		}
		if (xingOffset < 0 || bytes.length < xingOffset + 8) {
			return false;
		}
		if (bytes[xingOffset] == 'X' && bytes[xingOffset + 1] == 'i' && bytes[xingOffset + 2] == 'n' && bytes[xingOffset + 3] == 'g') {
			return true;
		}
		if (bytes[xingOffset] == 'I' && bytes[xingOffset + 1] == 'n' && bytes[xingOffset + 2] == 'f' && bytes[xingOffset + 3] == 'o') {
			return true;
		}
		return false;
	}

	boolean isVBRIFrame() {
		int vbriOffset = 36;
		if (bytes.length < vbriOffset + 26) { // minimum VBRI header size == 26
			return false;
		}
		return bytes[vbriOffset] == 'V' && bytes[vbriOffset + 1] == 'B' && bytes[vbriOffset + 2] == 'R' && bytes[vbriOffset + 3] == 'I';
	}

	public int getNumberOfFrames() {
		if (isXingFrame()) {
			int xingOffset = XING_HEADER_OFFSETS[channelMode][version];
			byte flags = bytes[xingOffset + 7];
			if ((flags & 0xF01) != 0) {
				return  ((bytes[xingOffset +  8] & 0xFF) << 24) |
						((bytes[xingOffset +  9] & 0xFF) << 16) |
						((bytes[xingOffset + 10] & 0xFF) << 8)  |
						( bytes[xingOffset + 11] & 0xFF);
			}
		} else if (isVBRIFrame()) {
			int vbriOffset = 36;
			return  ((bytes[vbriOffset + 14] & 0xFF) << 24) |
					((bytes[vbriOffset + 15] & 0xFF) << 16) |
					((bytes[vbriOffset + 16] & 0xFF) << 8)  |
					( bytes[vbriOffset + 17] & 0xFF);
		}
		return -1;
	}
	
	public boolean isCompatible(MP3Frame frame) {
		return layer == frame.layer && version == frame.version && frequency == frame.frequency && channelMode == frame.channelMode;
	}
}