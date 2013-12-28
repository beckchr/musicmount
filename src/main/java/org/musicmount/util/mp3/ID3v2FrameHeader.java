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

public class ID3v2FrameHeader {
	private String frameId;
	private int bodySize;
	private ID3v2Header tag;
	private boolean unsynchronization;
	private boolean compression;
	private boolean encryption;
	
	/*
	 * Parse header and consume bytes up the frame data
	 */
	public ID3v2FrameHeader(MP3Input data, ID3v2Header tag) throws IOException, ID3Exception {
		this.tag = tag;
		parse(data);
	}

	void parse(MP3Input data) throws IOException, ID3Exception {
		/*
		 * Frame Id
		 */
		if (tag.getVersion() == 2) { // $xx xx xx (three characters)
			frameId = new String(data.readFully(3), ID3v2Encoding.ISO_8859_1.getCharset());
		} else { // $xx xx xx xx (four characters)
			frameId = new String(data.readFully(4), ID3v2Encoding.ISO_8859_1.getCharset());
		}
		
		/*
		 * Size 
		 */
		if (tag.getVersion() == 2) { // $xx xx xx
			bodySize = ((data.readByte() & 0xFF) << 16) | ((data.readByte() & 0xFF) << 8) | (data.readByte() & 0xFF);
		} else if (tag.getVersion() == 3) { // $xx xx xx xx
			bodySize = data.readInt();
		} else { // 4 * %0xxxxxxx (sync-save integer)
			bodySize = data.readSyncsaveInt();
		}
		
		/*
		 * Flags
		 */
		if (tag.getVersion() > 2) { // $xx xx
			data.readByte(); // status flags
			byte formatFlags = data.readByte();
			int compressionMask = 0x00;
			int encryptionMask = 0x00;
			int groupingIdentityMask = 0x00;
			int unsynchronizationMask = 0x00;
			int dataLengthIndicatorMask = 0x00;
			if (tag.getVersion() == 3) { // %(compression)(encryption)(groupingIdentity)00000
				compressionMask = 0x80;
				encryptionMask = 0x40;
				groupingIdentityMask = 0x20;
			} else { // %0(groupingIdentity)00(compression)(encryption)(unsynchronization)(dataLengthIndicator)
				groupingIdentityMask = 0x40;
				compressionMask = 0x08;
				encryptionMask = 0x04;
				unsynchronizationMask = 0x02;
				dataLengthIndicatorMask = 0x01;
			}
			compression = (formatFlags & compressionMask) != 0;
			unsynchronization = (formatFlags & unsynchronizationMask) != 0;
			encryption = (formatFlags & encryptionMask) != 0;

			/*
			 * Skip flag attachments.
			 * If we wanted to keep the attachments, we'd need to read them in the order of the flags (version dependent).
			 */
			if ((formatFlags & groupingIdentityMask) != 0) {
				data.readByte();
				bodySize -= 1;
			}
			if (encryption) {
				data.readByte();
				bodySize -= 1;
			}
			if (compression || (formatFlags & dataLengthIndicatorMask) != 0) {
				data.readSyncsaveInt();
				bodySize -= 4;
			}
		}
	}
	
	public String getFrameId() {
		return frameId;
	}
	
	public int getBodySize() {
		return bodySize;
	}
	
	public ID3v2Header getTag() {
		return tag;
	}
	
	public boolean isCompression() {
		return compression;
	}
	
	public boolean isEncryption() {
		return encryption;
	}
	
	public boolean isUnsynchronization() {
		return unsynchronization;
	}
	
	public boolean isValid() {
		for (int i = 0; i < frameId.length(); i++) {
			if ((frameId.charAt(i) < 'A' || frameId.charAt(i) > 'Z') && (frameId.charAt(i) < '0' || frameId.charAt(i) > '9')) {
				return false;
			}
		}
		return bodySize > 0;
	}
	
	public boolean isPadding() {
		for (int i = 0; i < frameId.length(); i++) {
			if (frameId.charAt(0) != 0) {
				return false;
			}
		}
		return bodySize == 0;
	}
	
	@Override
	public String toString() {
		return String.format("%s[id=%s, bodysize=%d]", getClass().getSimpleName(), frameId, bodySize);
	}
}
