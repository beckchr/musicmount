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

	public static interface StopReadCondition {
		public static final StopReadCondition CONTINUE_READ = new StopReadCondition() {
			@Override
			public boolean stopRead(MP3Input data) {
				return false;
			}
		};
		public boolean stopRead(MP3Input data) throws IOException;
	}

	/**
	 * Searches the next audio frame.
	 * If <code>validatNextHeader</code> is set, it is guaranteed that the returned frame is either the last frame
	 * or is followed by a compatible header. This should be set when searching for the first frame to prevent
	 * creating a frame from a false sync.
	 * The stop condition is be used to make sure that the search ends as soon as the stop condition says so.
	 * the header bytes.
	 * @param data
	 * @param stopCondition
	 * @param validateNextHeader
	 * @return next frame or <code>null</code>
	 * @throws IOException
	 */
	public static MP3Frame readNextFrame(MP3Input data, StopReadCondition stopCondition, boolean validateNextHeader) throws IOException {
		int b0;
		int b1 = 0;
		
		while (!stopCondition.stopRead(data)) {
			b0 = b1;
			b1 = data.read();
			if (b1 == -1) {
				break;
			}
			if (b0 == 0xFF && (b1 & 0xE0) == 0xE0) { // first 11 bits should be 1
				data.mark(2); // set mark at b2
				int b2 = data.read();
				if (b2 == -1) {
					break;
				}
				int b3 = data.read();
				if (b3 == -1) {
					break;
				}
				try {
					Header header = new Header(b1, b2, b3);
					/*
					 * If the next header is invalid or not compatible, this is probably a false sync.
					 * The code gets a bit complex here, because we need to be able to reset() to b2 if
					 * the check fails. Thus, we have to reset() to b2 before doing a call to mark().
					 */
					if (validateNextHeader) {
						data.reset(); // reset input to b2
						data.mark(2 + header.getFrameSize()); // rest of frame (size - 2) + next header (4)
						/*
						 * create frame
						 */
						byte[] frameBytes = new byte[header.getFrameSize()];
						frameBytes[0] = (byte)0xFF;
						frameBytes[1] = (byte)b1;
						data.readFully(frameBytes, 2, frameBytes.length - 2);
						MP3Frame frame = new MP3Frame(header, frameBytes);
						/*
						 * read next header
						 */
						byte[] nextHeaderBytes;
						try {
							nextHeaderBytes = data.readFully(4);
						} catch (EOFException e) {
							return frame;
						}
						if ((nextHeaderBytes[0] & 0xFF) == 0xFF && (nextHeaderBytes[1] & 0xE0) == 0xE0) {
							Header nextHeader = new Header(nextHeaderBytes[1], nextHeaderBytes[2], nextHeaderBytes[3]);
							if (nextHeader.isCompatible(header)) {
								/*
								 * re-position to end of frame
								 */
								data.reset(); // reset input to b2
								data.skipFully(header.getFrameSize() - 2);
								return frame;
							}
						}
						/*
						 * there doesn't seem to be a compatible follow-up frame...
						 */
						data.reset(); // reset input to b2
					} else {
						/*
						 * do not care about the next header; just create the frame
						 */
						byte[] frameBytes = new byte[header.getFrameSize()];
						frameBytes[0] = (byte)0xFF;
						frameBytes[1] = (byte)b1;
						frameBytes[2] = (byte)b2;
						frameBytes[3] = (byte)b3;
						data.readFully(frameBytes, 4, frameBytes.length - 4);
						return new MP3Frame(header, frameBytes);
					}
				} catch (MP3Exception e) {
					data.reset(); // reset input to b2
				} catch (EOFException e) {
					break;
				}
			}
		}
		return null;
	}
	
	public static long calculateDuration(MP3Input data, long totalLength, StopReadCondition stopCondition) throws IOException, MP3Exception {
		MP3Frame firstFrame = MP3Frame.readNextFrame(data, stopCondition, true);
		if (firstFrame != null) {
			// check for Xing header
			int numberOfFrames = firstFrame.getNumberOfFrames();
			if (numberOfFrames > 0) { // from Xing/VBRI header
				return firstFrame.getHeader().getTotalDuration(numberOfFrames * firstFrame.getSize());
			} else { // scan file
				numberOfFrames = 1;

				long firstFramePosition = data.getPosition() - firstFrame.getSize();
				long frameSizeSum = firstFrame.getSize();

				int firstFrameBitrate = firstFrame.getHeader().getBitrate();
				long bitrateSum = firstFrameBitrate;
				boolean vbr = false;
				int cbrThreshold = 10000 / firstFrame.getHeader().getDuration(); // assume CBR after 10 seconds

				MP3Frame nextFrame = null;
				while (true) {
					if (numberOfFrames == cbrThreshold && !vbr && totalLength > 0) {
						return firstFrame.getHeader().getTotalDuration(totalLength - firstFramePosition);
					}
					if ((nextFrame = MP3Frame.readNextFrame(data, stopCondition, false)) == null) {
						break;
					}
					if (!nextFrame.getHeader().isCompatible(firstFrame.getHeader())) {
						throw new MP3Exception("Incompatible frame");
					}
					int bitrate = nextFrame.getHeader().getBitrate();
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
	
	public static class Header {
		private static final int MPEG_LAYER_RESERVED = 0;
		private static final int MPEG_VERSION_RESERVED = 1;
		private static final int MPEG_BITRATE_FREE = 0;
		private static final int MPEG_BITRATE_RESERVED = 15;
		private static final int MPEG_FRQUENCY_RESERVED = 3;

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

		// [channelMode][version]
		private static final int[][] XING_HEADER_OFFSETS = new int[][] {
				// 2.5  reserved  2        1
				{  21,    -1,    21,      36 }, // stereo
				{  21,    -1,    21,      36 }, // joint stereo
				{  21,    -1,    21,      36 }, // dual channel
				{  13,    -1,    13,      21 }, // mono
		};

		public static final int MPEG_LAYER_1 = 3;
		public static final int MPEG_LAYER_2 = 2;
		public static final int MPEG_LAYER_3 = 1;

		public static final int MPEG_VERSION_1   = 3;
		public static final int MPEG_VERSION_2   = 2;
		public static final int MPEG_VERSION_2_5 = 0;

		public static final int MPEG_CHANNEL_MODE_MONO = 3;

		private final int version;
		private final int layer;
		private final int frequency;
		private final int bitrate;
		private final int channelMode;
		private final int padding;

		Header(int b1, int b2, int b3) throws MP3Exception {
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

			if (getFrameSize() < 4) {
				throw new MP3Exception("Size must be at least four");
			}
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

		public int getFrameSize() {
			return ((SIZE_COEFFICIENTS[version][layer] * getBitrate() / getFrequency()) + padding) * SLOT_SIZES[layer];
		}
		
		public int getBitrate() {
			return BITRATES[bitrate][BITRATES_COLUMN[version][layer]];
		}
		
		public int getDuration() {
			return (int)getTotalDuration(getFrameSize());
		}

		public long getTotalDuration(long totalSize) {
			long duration = 1000L * (getSampleCount() * totalSize) / (getFrameSize() * getFrequency());
			if (getVersion() != MPEG_VERSION_1 && getChannelMode() == MPEG_CHANNEL_MODE_MONO) {
				duration /= 2;
			}
			return duration;
		}

		public boolean isCompatible(Header header) {
			return layer == header.layer && version == header.version && frequency == header.frequency && channelMode == header.channelMode;
		}
		
		public int getXingOffset() {
			return XING_HEADER_OFFSETS[channelMode][version];
		}

		public int getVBRIOffset() {
			return 36;
		}
	}

	private final byte[] bytes;
	private final Header header;

	MP3Frame(Header header, byte[] bytes) {
		this.header = header;
		this.bytes = bytes;
	}
	
	public int getSize() {
		return bytes.length;
	}
	
	public Header getHeader() {
		return header;
	}

	boolean isXingFrame() {
		int xingOffset = header.getXingOffset();
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
		int vbriOffset = header.getVBRIOffset();
		if (bytes.length < vbriOffset + 26) { // minimum VBRI header size == 26
			return false;
		}
		return bytes[vbriOffset] == 'V' && bytes[vbriOffset + 1] == 'B' && bytes[vbriOffset + 2] == 'R' && bytes[vbriOffset + 3] == 'I';
	}

	public int getNumberOfFrames() {
		if (isXingFrame()) {
			int xingOffset = header.getXingOffset();
			byte flags = bytes[xingOffset + 7];
			if ((flags & 0xF01) != 0) {
				return  ((bytes[xingOffset +  8] & 0xFF) << 24) |
						((bytes[xingOffset +  9] & 0xFF) << 16) |
						((bytes[xingOffset + 10] & 0xFF) << 8)  |
						( bytes[xingOffset + 11] & 0xFF);
			}
		} else if (isVBRIFrame()) {
			int vbriOffset = header.getVBRIOffset();
			return  ((bytes[vbriOffset + 14] & 0xFF) << 24) |
					((bytes[vbriOffset + 15] & 0xFF) << 16) |
					((bytes[vbriOffset + 16] & 0xFF) << 8)  |
					( bytes[vbriOffset + 17] & 0xFF);
		}
		return -1;
	}
}