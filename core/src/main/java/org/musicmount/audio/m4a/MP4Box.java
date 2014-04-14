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
package org.musicmount.audio.m4a;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

import org.musicmount.util.PositionInputStream;
import org.musicmount.util.PositionLengthInputStream;

/**
 * MP4 box base class.
 * @param <I> PositionInputStream
 */
public class MP4Box<I extends PositionInputStream> {
	protected static final String ASCII = "ISO8859_1";

	private final I input;
	private final MP4Box<?> parent;
	private final String type;

	protected final DataInput data;

	private MP4Atom child;

	/**
	 * Create new MP4 container.
	 * @param input box input
	 * @param parent parent box
	 * @param type box type
	 */
	public MP4Box(I input, MP4Box<?> parent, String type) {
		this.input = input;
		this.parent = parent;
		this.type = type;
		this.data = new DataInputStream(input);
	}

	public String getType() {
		return type;
	}

	public MP4Box<?> getParent() {
		return parent;
	}

	public long getPosition() {
		return input.getPosition();
	}

	public I getInput() {
		return input;
	}
	
	protected MP4Atom getChild() {
		return child;
	}
	
	public MP4Atom nextChild() throws IOException {
		if (child != null) {
			child.skip();
		}
		int atomLength = data.readInt();
		byte[] typeBytes = new byte[4];
		data.readFully(typeBytes);
		String atomType = new String(typeBytes, ASCII);
		PositionLengthInputStream atomInput = null;
		if (atomLength == 1) { // extended length
			atomInput = new PositionLengthInputStream(input, 16, data.readLong() - 16);
		} else {
			atomInput = new PositionLengthInputStream(input, 8, atomLength - 8);
		}
		return child = new MP4Atom(atomInput, this, atomType);
	}

	public MP4Atom nextChild(String expectedTypeExpression) throws IOException {
		MP4Atom atom = nextChild();
		if (atom.getType().matches(expectedTypeExpression)) {
			return atom;
		}
		throw new IOException ("atom type mismatch, expected " + expectedTypeExpression + ", got " + atom.getType());
	}
}