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

public class ID3v2Header {
	private int version = 0;
	private int revision = 0;
	private int headerSize = 0; // size of header, including extended header (with attachments)
	private int totalTagSize = 0; // everything, i.e. inluding tag header, extended header, footer & padding
	private int paddingSize = 0; // size of zero padding after frames
	private int footerSize = 0; // size of footer (version 4 only)
	private boolean unsynchronization;
	private boolean compression;
	
	/*
	 * Parse tag header and consume bytes up the first frame header
	 */
	public ID3v2Header(MP3Input data) throws IOException, ID3v2Exception {
		parse(data);
	}
	
	void parse(MP3Input data) throws IOException, ID3v2Exception {
		long startPosition = data.getPosition();
		
		/*
		 * Identifier: "ID3"
		 */
		String id = new String(data.readFully(3), ID3v2Encoding.ISO_8859_1.getCharset());
		if (!"ID3".equals(id)) {
			throw new ID3v2Exception("Invalid ID3 identifier: " + id);
		}
		
		/*
		 * Version: $02, $03 or $04
		 */
		version = data.readByte();
		if (version != 2 && version != 3 && version != 4) {
			throw new ID3v2Exception("Unsupported ID3v2 version: " + version);
		}
		
		/*
		 * Revision: $xx
		 */
		revision = data.readByte();

		/*
		 * Flags (evaluated below)
		 */
		byte flags = data.readByte();
		
		/*
		 * Size: 4 * %0xxxxxxx (sync-save integer)
		 */
		totalTagSize = 10 + data.readSyncsaveInt();

		/*
		 * Evaluate flags
		 */
		if (version == 2) { // %(unsynchronisation)(compression)000000
			unsynchronization = (flags & 0x80) != 0;
			compression = (flags & 0x40) != 0;
		} else { // %(unsynchronisation)(extendedHeader)(experimentalIndicator)(version == 3 ? 0 : footerPresent)0000
			unsynchronization = (flags & 0x80) != 0;
			
			/*
			 * Extended Header
			 */
			if ((flags & 0x40) != 0) {
				if (version == 3) {
					/*
					 * Extended header size: $xx xx xx xx (6 or 10 if CRC data present)
					 * In version 3, the size excludes itself.
					 */
					int extendedHeaderSize = data.readInt();
					
					/*
					 * Extended Flags: $xx xx (skip)
					 */
					data.readByte(); // flags...
					data.readByte(); // more flags...
					
					/*
					 * Size of padding: $xx xx xx xx
					 */
					paddingSize = data.readInt();
					
					/*
					 * consume the rest
					 */
					data.skipFully(extendedHeaderSize - 6);
				} else {
					/*
					 * Extended header size: 4 * %0xxxxxxx (sync-save integer)
					 * In version 4, the size includes itself.
					 */
					int extendedHeaderSize = data.readSyncsaveInt();
					
					/*
					 * consume the rest
					 */
					data.skipFully(extendedHeaderSize - 4);
				}
			}
			
			/*
			 * Footer Present
			 */
			if (version >= 4 && (flags & 0x10) != 0) { // footer present
				footerSize = 10;
				totalTagSize += 10;
			}
		}

		headerSize = (int)(data.getPosition() - startPosition);
	}
	
	public int getVersion() {
		return version;
	}
	
	public int getRevision() {
		return revision;
	}
	
	public int getTotalTagSize() {
		return totalTagSize;
	}
	
	public boolean isUnsynchronization() {
		return unsynchronization;
	}
	
	public boolean isCompression() {
		return compression;
	}
	
	public int getHeaderSize() {
		return headerSize;
	}
	
	public int getFooterSize() {
		return footerSize;
	}
	
	public int getPaddingSize() {
		return paddingSize;
	}
	
	@Override
	public String toString() {
		return String.format("%s[version=%s, tagSize=%d]", getClass().getSimpleName(), version, totalTagSize);
	}
}
