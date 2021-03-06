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
package org.musicmount.builder.impl;


public enum ImageType {
	Thumbnail(64, 64, false, "JPG", "image/jpeg", "thumbnail.jpg"),
	Tile(192, 128, true, "JPG", "image/jpeg", "tile.jpg"),
	Artwork(256, 512, true, "JPG", "image/jpeg", "artwork.jpg");

	private final int targetWidth;
	private final int targetHeight;
	private final boolean fitToTarget;
	private final String fileType;
	private final String fileName;
	private final String mimeType;
	
	private ImageType(int targetWidth, int targetHeight, boolean fitToTarget, String fileType, String mimeType, String fileName) {
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.fitToTarget = fitToTarget;
		this.fileType = fileType;
		this.mimeType = mimeType;
		this.fileName = fileName;
	}
	
	public double getScaleFactor(int sourceWidth, int sourceHeight) {
		double scaleX = (double)targetWidth / (double)sourceWidth;
		double scaleY = (double)targetHeight / (double)sourceHeight;
		return fitToTarget ? Math.min(scaleX, scaleY) : Math.max(scaleX, scaleY);
	}
	
	public String getFileType() {
		return fileType;
	}
	
	public String getMimeType() {
		return mimeType;
	}
	
	public String getFileName() {
		return fileName;
	}
}
