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
package org.musicmount.live;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.musicmount.builder.impl.ImageType;
import org.musicmount.builder.impl.ResourceLocator;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.io.Resource;

public class LiveServlet extends HttpServlet implements ResourceLocator {
	private static final long serialVersionUID = 1L;

	private static final String SERVICE_INDEX_PATH = "serviceIndex";
	private static final String ARTIST_INDEX_PATH = "artistIndex";
	private static final String ALBUM_INDEX_PATH = "albumIndex";
	private static final String TRACK_INDEX_PATH = "trackIndex";
	private static final String ALBUM_PATH = "album";
	private static final String ALBUM_COLLECTION_PATH = "albumCollection";
	private static final String ALBUM_IMAGE_PATH = "albumImage";

	private static final String ARTIST_TYPE_PARAM = "artistType";
	private static final String IMAGE_TYPE_PARAM = "imageType";

	private static final String ARTIST_ID_PARAM = "artistId";
	private static final String ALBUM_ID_PARAM = "albumId";

	private final LiveContext context;

	public LiveServlet(LiveContext context) {
		this.context = context;
	}

	@Override
	public Resource getResource(String path) {
		return null;
	}

	@Override
	public String getServiceIndexPath() {
		return SERVICE_INDEX_PATH;
	}

	@Override
	public String getAlbumIndexPath() {
		return ALBUM_INDEX_PATH;
	}

	@Override
	public String getTrackIndexPath() {
		return context.isNoTrackIndex() ? null : TRACK_INDEX_PATH;
	}

	@Override
	public String getArtistIndexPath(ArtistType artistType) {
		return String.format("%s?%s=%s", ARTIST_INDEX_PATH, ARTIST_TYPE_PARAM, artistType.name());
	}
	
	@Override
	public String getAlbumCollectionPath(Artist artist) {
		return String.format("%s?%s=%s&%s=%d", ALBUM_COLLECTION_PATH, ARTIST_TYPE_PARAM, artist.getArtistType().name(), ARTIST_ID_PARAM, artist.getArtistId());
	}

	@Override
	public String getAlbumImagePath(Album album, ImageType type) {
		if (album.artworkAssetResource() == null) {
			return null;
		}
		return String.format("%s?%s=%s&%s=%d", ALBUM_IMAGE_PATH, IMAGE_TYPE_PARAM, type.name(), ALBUM_ID_PARAM, album.getAlbumId());
	}

	@Override
	public String getAlbumPath(Album album) {
		return String.format("%s?%s=%d", ALBUM_PATH, ALBUM_ID_PARAM, album.getAlbumId());
	}
	
	private ArtistType parseArtistType(String string) {
		try {
			return ArtistType.valueOf(string);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
	
	private ImageType parseImageType(String string) {
		try {
			return ImageType.valueOf(string);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
	
	private Artist findArtist(String artistTypeName, String artistIdString) {
		try {
			return context.getArtist(parseArtistType(artistTypeName), Long.valueOf(artistIdString));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Album findAlbum(String albumIdString) {
		try {
			return context.getAlbum(Long.valueOf(albumIdString));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Resource findArtworkAssetResource(String albumIdString) {
		Album album = findAlbum(albumIdString);
		return album != null ? album.artworkAssetResource() : null;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo();
		if (path == null) {
			path = "";
		} else {
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		if (path.length() == 0) {
			path = SERVICE_INDEX_PATH;
		}

		ByteArrayOutputStream content = new ByteArrayOutputStream(1024);
		switch (path) {
		case SERVICE_INDEX_PATH:
			context.formatServiceIndex(this, content);
			resp.setContentType("text/json");
			resp.setCharacterEncoding("UTF-8");
			break;
		case ARTIST_INDEX_PATH:
			ArtistType artistType = parseArtistType(req.getParameter(ARTIST_TYPE_PARAM));
			if (artistType != null) {
				context.formatArtistIndex(this, content, artistType);
				resp.setContentType("text/json");
				resp.setCharacterEncoding("UTF-8");
			} else {
				resp.sendError(404);
			}
			break;
		case ALBUM_INDEX_PATH:
			context.formatAlbumIndex(this, content);
			resp.setContentType("text/json");
			resp.setCharacterEncoding("UTF-8");
			break;
		case TRACK_INDEX_PATH:
			context.formatTrackIndex(this, content);
			resp.setContentType("text/json");
			resp.setCharacterEncoding("UTF-8");
			break;
		case ALBUM_COLLECTION_PATH:
			Artist artist = findArtist(req.getParameter(ARTIST_TYPE_PARAM), req.getParameter(ARTIST_ID_PARAM));
			if (artist != null) {
				context.formatAlbumCollection(this, content, artist);
				resp.setContentType("text/json");
				resp.setCharacterEncoding("UTF-8");
			} else {
				resp.sendError(404);
			}
			break;
		case ALBUM_PATH:
			Album album = findAlbum(req.getParameter(ALBUM_ID_PARAM));
			if (album != null) {
				context.formatAlbum(this, content, album);
				resp.setContentType("text/json");
				resp.setCharacterEncoding("UTF-8");
			} else {
				resp.sendError(404);
			}
			break;
		case ALBUM_IMAGE_PATH:
			ImageType imageType = parseImageType(req.getParameter(IMAGE_TYPE_PARAM));
			if (imageType != null) {
				Resource assetResource = findArtworkAssetResource(req.getParameter(ALBUM_ID_PARAM));
				if (assetResource != null) {
					context.formatImage(assetResource, content, imageType);
					resp.setContentType(imageType.getMimeType());
				} else {
					resp.sendError(404);
				}
			} else {
				resp.sendError(404);					
			}
			break;
		default:
			resp.sendError(404);					
		}

		if (content.size() > 0 && !resp.isCommitted()) {
			resp.setContentLength(content.size());
			resp.getOutputStream().write(content.toByteArray());
			resp.getOutputStream().flush();		
		}
	}
}
