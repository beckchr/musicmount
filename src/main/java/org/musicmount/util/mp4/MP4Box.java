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

/**
 * Box interface shared between {@link org.musicmount.util.mp4.MP4Input}
 * and {@link org.musicmount.util.mp4.MP4Atom}.
 */
public interface MP4Box {
	/**
	 * Box type (atom name)
	 * @return box type
	 */
	public String getType();

	/**
	 * @return parent box
	 */
	public MP4Box getParent();

	/**
	 * @return current reading position (relative to parent box)
	 */
	public long getPosition();
}