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

public enum ID3v2Encoding {
	ISO_8859_1("ISO-8859-1", 1),
	UTF_16("UTF-16", 2),
	UTF_16BE("UTF-16BE", 2),
	UTF_8("UTF-8", 1);
	
	private final String charsetName;
	private final int zeroBytes;
	
	private ID3v2Encoding(String charsetName, int zeroBytes) {
		this.charsetName = charsetName;
		this.zeroBytes = zeroBytes;
	}
	
	public String getCharset() {
		return charsetName;
	}
	
	public int getZeroBytes() {
		return zeroBytes;
	}
}
