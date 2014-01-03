/*
 * Copyright 2013-2014 Odysseus Software GmbH
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
package org.musicmount.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream filter that keeps track of the current read position
 * and has a read length limit.
 */
public class PositionLengthInputStream extends PositionInputStream {
	private final long endPosition;
	
	public PositionLengthInputStream(InputStream delegate, long position, long length) throws IOException {
		super(delegate, position);
		this.endPosition = position + length;
	}
	
	public long getRemainingLength() {
		return endPosition - getPosition();
	}
	
	@Override
	public int read() throws IOException {
		if (getPosition() == endPosition) {
			return -1;
		}
		return super.read();
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (getPosition() + len > endPosition) {
			len  = (int)(endPosition - getPosition());
		}
		return super.read(b, off, len);
	}
	
	@Override
	public long skip(long n) throws IOException {
		if (getPosition() + n > endPosition) {
			n  = (int)(endPosition - getPosition());
		}
		return super.skip(n);
	}
}
