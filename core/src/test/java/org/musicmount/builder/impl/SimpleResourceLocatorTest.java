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

import java.io.File;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.AlbumArtist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Track;
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;

public class SimpleResourceLocatorTest {
	ResourceProvider resourceProvider = new FileResourceProvider();

	@Test
	public void testGetAlbumCollectionPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Assert.assertEquals("albumArtists/10/01-albums.json", resourceLocator.getAlbumCollectionPath(new AlbumArtist(0x1001, "foo")));
	}

	Album createAlbum(long albumId, AlbumArtist artist, boolean artworkAvailable) {
		Resource dummyFile = resourceProvider.newResource(String.format("%d.mp3", albumId));
		Track track = new Track(null, dummyFile, artworkAvailable, false, null, null, null, null, null, null, null);
		Album album = new Album(null);
		album.setAlbumId(albumId);
		album.getTracks().add(track);
		track.setAlbum(album);
		if (artist != null) {
			album.setArtist(artist);
			artist.getAlbums().put(album.getTitle(), album);
		}
		return album;
	}
	
	@Test
	public void testGetAlbumImagePath() {
		Album album;
		
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		album = createAlbum(0x1001, null, true);
		Assert.assertEquals("albums/10/01/" + ImageType.Artwork.getFileName(), resourceLocator.getAlbumImagePath(album, ImageType.Artwork));
		Assert.assertEquals("albums/10/01/" + ImageType.Tile.getFileName(), resourceLocator.getAlbumImagePath(album, ImageType.Tile));
		Assert.assertEquals("albums/10/01/" + ImageType.Thumbnail.getFileName(), resourceLocator.getAlbumImagePath(album, ImageType.Thumbnail));

		album = createAlbum(0x1001, null, false); // no artwork available
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Artwork));
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Tile));
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Thumbnail));

		resourceLocator = new SimpleResourceLocator(null, false, true, false); // noImages
		album = createAlbum(0x1001, null, true);
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Artwork));
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Tile));
		Assert.assertNull(resourceLocator.getAlbumImagePath(album, ImageType.Thumbnail));
	}

	@Test
	public void testGetAlbumIndexPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Assert.assertEquals("albums/index.json", resourceLocator.getAlbumIndexPath());
	}

	@Test
	public void testGetAlbumPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Album album = new Album("foo");
		album.setAlbumId(0x1001);
		Assert.assertEquals("albums/10/01/album.json", resourceLocator.getAlbumPath(album));
	}

	@Test
	public void testGetArtistIndexPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Assert.assertEquals("albumArtists/index.json", resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist));
		Assert.assertEquals("artists/index.json", resourceLocator.getArtistIndexPath(ArtistType.TrackArtist));
	}

	@Test
	public void testGetTrackIndexPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Assert.assertEquals("tracks/index.json", resourceLocator.getTrackIndexPath());
		resourceLocator = new SimpleResourceLocator(null, false, false, true);
		Assert.assertNull(resourceLocator.getTrackIndexPath());
	}

	@Test
	public void testFile() throws URISyntaxException {
		Resource outputFolder = resourceProvider.newResource(new File(getClass().getResource("/sample-assets").toURI()).toPath());
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(outputFolder, false, false, false);
		Assert.assertEquals(outputFolder.resolve("foo/bar.json"), resourceLocator.getResource("foo/bar.json"));
	}

	@Test
	public void testGetServiceIndexPath() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Assert.assertEquals("index.json", resourceLocator.getServiceIndexPath());
	}
	
	@Test
	public void testHugeId() {
		SimpleResourceLocator resourceLocator = new SimpleResourceLocator(null, false, false, false);
		Album album = new Album("foo");
		album.setAlbumId(0x1001);
		Assert.assertEquals("albums/10/01/album.json", resourceLocator.getAlbumPath(album));
	}


}
