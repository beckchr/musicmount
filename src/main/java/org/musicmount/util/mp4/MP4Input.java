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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Input box.
 */
public final class MP4Input extends FilterInputStream implements MP4Box {
    private long position;
    private MP4Atom child;
    
    public MP4Input (InputStream delegate) throws IOException {
        super(delegate);
    }

	public MP4Box getParent() {
		return null;
	}

    public String getType() {
    	return "";
    }

	public MP4Atom nextChild() throws IOException {
		if (child != null) {
			child.skip(child.getRemaining());
		}
		return child = new MP4Atom(this);
	}

	public MP4Atom nextChild(String expectedTypeExpression) throws IOException {
		MP4Atom atom = nextChild();
		if (atom.getType().matches(expectedTypeExpression)) {
			return atom;
		}
		throw new IOException ("atom type mismatch, expected " + expectedTypeExpression + ", got " + atom.getType());
	}

	public MP4Atom nextChildUpTo(String expectedTypeExpression) throws IOException {
		while (true) {
			MP4Atom atom = nextChild();
			if (atom.getType().matches(expectedTypeExpression)) {
				return atom;
			}
		}
	}

    public int read() throws IOException {
        int data = super.read();
        if (data >= 0) {
        	position++;
        }
        return data;
    }

    public int read(byte[] b, int off, int len) throws IOException {
    	long p = position;
        int read = super.read(b, off, len);
        position = p + read;
        return read;
    }
  
    @Override
    public int read(byte[] b) throws IOException {
    	long p = position;
        int read = super.read(b);
        position = p + read;
        return read;
    }
    
    public long skip(long n) throws IOException {
    	long p = position;
        long skipped = super.skip(n);
        position = p + skipped;
        return skipped;
    }
    
    @Override
    public synchronized void reset() throws IOException {
    	throw new IOException("mark/reset not supported");
    }
    
    public long getPosition() {
        return position;
    }
    
    public String toString() {
    	return "mp4[pos=" + position + "]";
    }
}
