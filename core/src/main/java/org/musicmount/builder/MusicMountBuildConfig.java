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
package org.musicmount.builder;

import java.text.Normalizer;

public final class MusicMountBuildConfig implements Cloneable {

	private boolean retina = false;
	private boolean pretty = false;
	private boolean full = false;
	private boolean noImages = false;
	private boolean xml = false;
	private boolean grouping = false;
	private boolean unknownGenre = false;
	private boolean noTrackIndex = false;
	private boolean noVariousArtists = false;
	private boolean directoryIndex = false;
	private Normalizer.Form normalizer = null;

	@Override
	public MusicMountBuildConfig clone() {
		try {
			return (MusicMountBuildConfig)super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
	
	public boolean isRetina() {
		return retina;
	}
	public void setRetina(boolean retina) {
		this.retina = retina;
	}

	public boolean isPretty() {
		return pretty;
	}
	public void setPretty(boolean pretty) {
		this.pretty = pretty;
	}

	public boolean isFull() {
		return full;
	}
	public void setFull(boolean full) {
		this.full = full;
	}

	public boolean isNoImages() {
		return noImages;
	}
	public void setNoImages(boolean noImages) {
		this.noImages = noImages;
	}

	public boolean isXml() {
		return xml;
	}
	public void setXml(boolean xml) {
		this.xml = xml;
	}

	public boolean isGrouping() {
		return grouping;
	}
	public void setGrouping(boolean grouping) {
		this.grouping = grouping;
	}

	public boolean isUnknownGenre() {
		return unknownGenre;
	}

	public void setUnknownGenre(boolean unknownGenre) {
		this.unknownGenre = unknownGenre;
	}
	
	public boolean isNoTrackIndex() {
		return noTrackIndex;
	}
	public void setNoTrackIndex(boolean noTrackIndex) {
		this.noTrackIndex = noTrackIndex;
	}

	public boolean isNoVariousArtists() {
		return noVariousArtists;
	}
	public void setNoVariousArtists(boolean noVariousArtists) {
		this.noVariousArtists = noVariousArtists;
	}

	public boolean isDirectoryIndex() {
		return directoryIndex;
	}
	public void setDirectoryIndex(boolean directoryIndex) {
		this.directoryIndex = directoryIndex;
	}

	public Normalizer.Form getNormalizer() {
		return normalizer;
	}
	public void setNormalizer(Normalizer.Form normalizer) {
		this.normalizer = normalizer;
	}
}
