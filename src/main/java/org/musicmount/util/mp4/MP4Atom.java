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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * MP4 atom.
 */
public class MP4Atom implements MP4Box {
	/**
	 * Convert leading 4 (big endian) bytes to int
	 */
	static int beb2int(final byte[] x) {
        return ( (int)x[0]         << 24) |
               (((int)x[1] & 0xFF) << 16) |
               (((int)x[2] & 0xFF) <<  8) |
               ( (int)x[3] & 0xFF       );
    }

	/**
	 * Convert int to 4 (big endian) bytes
	 */
    static byte[] int2beb(final int x) {
		byte[] buf = new byte[4];
    	buf[0] = (byte)(x >> 24);
    	buf[1] = (byte)(x >> 16);
    	buf[2] = (byte)(x >>  8);
    	buf[3] = (byte) x       ;
		return buf;
    }

    class AtomInput extends FilterInputStream {
    	AtomInput(MP4Input in) {
    		super(in);
    	}
    	
    	public int read() throws IOException {
    		if (getRemaining() < 1) {
    			throw new IOException("cannot read beyond atom: " + MP4Atom.this);
    		}
    		return super.read();
    	}

    	public int read(byte[] b, int off, int len) throws IOException {
    		if (getRemaining() < len) {
    			throw new IOException("cannot read beyond atom: " + MP4Atom.this);
    		}
    		return super.read(b, off, len);
    	}

    	public int read(byte[] b) throws IOException {
    		if (getRemaining() < b.length) {
    			throw new IOException("cannot read beyond atom: " + MP4Atom.this);
    		}
    		return super.read(b);
    	}
    }

	private static final String ASCII = "ISO8859_1";

    private MP4Input input;
	private MP4Box parent;
	private MP4Atom child;
	private long start;
	private long length;
	private String type;
	private DataInputStream data;

	/**
	 * Create top-level atom
	 * @param input
	 * @throws IOException
	 */
	protected MP4Atom(MP4Input input) throws IOException {
		this(input, input);
	}

	/**
	 * Create inner atom
	 * @param input
	 * @throws IOException
	 */
	protected MP4Atom(MP4Input input, MP4Atom parent) throws IOException {
		this(input, (MP4Box)parent);
	}

	private MP4Atom(MP4Input input, MP4Box parent) throws IOException {
		this.input = input;
		this.parent = parent;
		data = new DataInputStream(new AtomInput(input));
		start = input.getPosition();
		length = 8; // at least...
		length = readInt();
		if (length == 1) { // extended length
			length = 16;
			type = new String(int2beb(readInt()), ASCII);
			length = readLong();
		} else {
			type = new String(int2beb(readInt()), ASCII);
		}
	}

	public MP4Box getParent() {
		return parent;
	}

	public String getType() {
		return type;
	}
	
	public int getTypeAsInt() {
		return beb2int(type.getBytes());
	}
	
	public long getPosition() {
		return input.getPosition() - start;
	}

	/**
	 * @return atom length (bytes)
	 */
	public long getLength() {
		return length;
	}

	/**
	 * @return start offset relative to parent box
	 */
	public long getOffset() {
		return parent.getPosition() - getPosition();
	}

	/**
	 * @return number of remaining bytes
	 */
	public long getRemaining() {
		return getLength() - getPosition();
	}
	
	public boolean hasMoreChildren() {
		return getPosition() + (child != null ? child.getRemaining() : 0) < getLength();
	}

	public MP4Atom nextChild() throws IOException {
		if (child != null) {
			skip(child.getRemaining());
		}
		if (getRemaining() <= 0) {
			throw new IOException ("cannot read beyond atom: " + this);
		}
		return child = new MP4Atom(input, this);
	}

	public MP4Atom nextChild(String expectedTypeExpression) throws IOException {
		MP4Atom atom = nextChild();
		if (atom.getType().matches(expectedTypeExpression)) {
			return atom;
		}
		throw new IOException ("atom type mismatch, expected " + expectedTypeExpression + ", got " + atom.getType());
	}

	public MP4Atom nextChildUpTo(String expectedTypeExpression) throws IOException {
		while (getRemaining() > 0) {
			MP4Atom atom = nextChild();
			if (atom.getType().matches(expectedTypeExpression)) {
				return atom;
			}
		}
		throw new IOException ("atom type mismatch, not found: " + expectedTypeExpression);
	}

	public boolean readBoolean() throws IOException {
		return data.readBoolean();
	}

	public byte readByte() throws IOException {
		return data.readByte();
	}

	public int readUnsignedByte() throws IOException {
		return data.readUnsignedByte();
	}

	public char readChar() throws IOException {
		return data.readChar();
	}

	public void readBytes(byte[] b) throws IOException {
		int offset = 0;
		while (offset < b.length) {
			long read = data.read(b, offset, b.length - offset);
		    if (read < 0) {
		        throw new IOException("read failed: " + this);
		    } else {
		    	offset += read;
		    }
		}
	}

	public byte[] readBytes(int len) throws IOException {
		byte[] bytes = new byte[len];
		readBytes(bytes);
		return bytes;
	}

	public byte[] readBytes() throws IOException {
		return readBytes((int)getRemaining());
	}

	public int readInt() throws IOException {
		return data.readInt();
	}

	public long readLong() throws IOException {
		return data.readLong();
	}

	public short readShort() throws IOException {
		return data.readShort();
	}

	public BigDecimal readShortFixedPoint() throws IOException {
		int integer = readByte();
		int decimal = readUnsignedByte();
		return new BigDecimal(String.valueOf(integer) + "." + String.valueOf(decimal));
	}

	public BigDecimal readIntegerFixedPoint() throws IOException {
		int integer = readShort();
		int decimal = readUnsignedShort();
		return new BigDecimal(String.valueOf(integer) + "." + String.valueOf(decimal));
	}

	public int readUnsignedShort() throws IOException {
		return data.readUnsignedShort();
	}

	public String readString(int len, String enc) throws IOException {
		String s = new String(readBytes(len), enc);
		int end = s.indexOf(0);
		return end < 0 ? s : s.substring(0, end);
	}

	public String readString(String enc) throws IOException {
		return readString((int)getRemaining(), enc);
	}

	public void skip(long length) throws IOException {
		if (getRemaining() < length) {
			throw new IOException ("cannot skip" + length + " bytes: " + this);
		}
		long skipped = 0;
		while (skipped < length) {
			long current = data.skip(length - skipped);
		    if (current < 0) {
		        throw new EOFException("could not skip...");
		    } else {
		        skipped += current;
		    }
		}
	}

	private StringBuffer appendPath(StringBuffer s, MP4Box box) {
		if (box.getParent() != null) {
			appendPath(s, box.getParent());
			s.append("/");
		}
		return s.append(box.getType());
	}

	public String getPath() {
		return appendPath(new StringBuffer(), this).toString();
	}
	
	public String toString() {
		StringBuffer s = new StringBuffer();
		appendPath(s, this);
		s.append("[off=");
		s.append(getOffset());
		s.append(",pos=");
		s.append(getPosition());
		s.append(",len=");
		s.append(length);
		s.append("]");
		return s.toString();
	}
}
