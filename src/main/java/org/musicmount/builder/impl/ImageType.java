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
package org.musicmount.builder.impl;

import java.awt.Dimension;

public enum ImageType {
	Thumbnail(32, 32, "PNG", "thumbnail.png"),	// for best results: width and height are exact
//	Icon(64, 64, "PNG", "icon.png"),			// for best results: width and height are exact
	Tile(192, 128, "JPG", "tile.jpg"),			// for best results: width or height is exact
	Artwork(256, 560, "JPG", "artwork.jpg");	// for best results: width is exact, height is maximum

	private final int maxWidth;
	private final int maxHeight;
	private final String fileType;
	private final String fileName;
	
	private ImageType(int maxWidth, int maxHeight, String fileType, String fileName) {
		this.maxWidth = maxWidth;
		this.maxHeight = maxHeight;
		this.fileType = fileType;
		this.fileName = fileName;
	}
	
	public Dimension getMaxSize() {
		return new Dimension(maxWidth, maxHeight);
	}

	public String getFileType() {
		return fileType;
	}
	
	public String getFileName() {
		return fileName;
	}
}
