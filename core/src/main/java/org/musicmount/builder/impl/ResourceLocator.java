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

import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.io.Resource;

public interface ResourceLocator {
	public Resource getResource(String path);
	
	public String getServiceIndexPath();	
	public String getArtistIndexPath(ArtistType artistType);	
	public String getAlbumIndexPath();	

	public String getAlbumCollectionPath(Artist artist);
	public String getAlbumPath(Album album);
	public String getAlbumImagePath(Album album, ImageType type);
}
