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
package org.musicmount.builder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.LocalStrings;
import org.musicmount.builder.impl.ResourceLocator;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.impl.SimpleAssetLocator;
import org.musicmount.builder.impl.SimpleResourceLocator;
import org.musicmount.builder.impl.AssetParser;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.model.Album;
import org.musicmount.builder.model.Artist;
import org.musicmount.builder.model.ArtistType;
import org.musicmount.builder.model.Library;
import org.musicmount.util.LoggingUtil;

public class MusicMountBuilder {
	static final Logger LOGGER = Logger.getLogger(MusicMountBuilder.class.getName());

    /**
     * prevent ImageIO from using file cache
     */
	static {
		ImageIO.setUseCache(false); // TODO not sure if this is really useful...
	}

	/**
	 * API version string
	 */
	public static final String API_VERSION = "1.0";	

	/**
	 * Name of asset store file.
	 */
	static final String ASSET_STORE = ".musicmount.gz";	

	public static void generateResponseFiles(
			Library library,
			File outputFolder,
			ResponseFormatter<?> formatter,
			ResourceLocator resourceLocator,
			AssetLocator assetLocator) throws Exception {

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating service index...");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getServiceIndexPath()))) {
			formatter.formatServiceIndex(resourceLocator, output);
		}

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating album artist index...");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist)))) {
			formatter.formatArtistIndex(library.getAlbumArtists().values(), ArtistType.AlbumArtist, output, resourceLocator);
		}
		for (Artist artist : library.getAlbumArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for album artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumCollectionPath(artist)))) {
				formatter.formatAlbumCollection(artist, output, resourceLocator);
			}
		}

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating artist index...");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getArtistIndexPath(ArtistType.TrackArtist)))) {
			formatter.formatArtistIndex(library.getTrackArtists().values(), ArtistType.TrackArtist, output, resourceLocator);
		}
		for (Artist artist : library.getTrackArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumCollectionPath(artist)))) {
				formatter.formatAlbumCollection(artist, output, resourceLocator);
			}
		}

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating album index...");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumIndexPath()))) {
			formatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);
		}
		for (Album album : library.getAlbums()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album: " + album.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumPath(album)))) {
				formatter.formatAlbum(album, output, resourceLocator, assetLocator);
			}
		}
	}
	
	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder> <mount_folder>", command));
		System.err.println();
		System.err.println("Generate MusicMount site from music in <music_folder> into <mount_folder>");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music path prefix, default is 'music'");
		System.err.println("       --retina           double image resolution");
		System.err.println("       --full             full parse, don't use asset store");
		System.err.println("       --unknownGenre     report missing genre as 'Unknown'");
		System.err.println("       --noVariousArtists exclude 'Various Artists' from album artist index");
		System.err.println("       --noDirectoryIndex use 'path/index.ext' instead of 'path/'");
		System.err.println("       --pretty           pretty-print JSON documents");
//		System.err.println("       --normalize <form> normalize asset paths, one of NFC|NFD");
//		System.err.println("       --noImages         do not generate images");
//		System.err.println("       --xml              generate XML instead of JSON");
		System.err.close();
		System.exit(1);	
	}
    
	/**
	 * Generate JSON + images
	 * @param args inputFolder, outputFolder, baseURL
	 * @throws Exception
	 */
	public static void execute(String command, String[] args) throws Exception {
		if (args.length < 2) {
			exitWithError(command, "missing arguments");
		}
		String optionMusic = "music";
		boolean optionRetina = false;
		boolean optionPretty = false;
		boolean optionFull = false;
		boolean optionDebug = false;
		boolean optionNoImages = false;
		boolean optionXML = false;
		boolean optionUnknownGenre = false;
		boolean optionNoVariousArtists = false;
		boolean optionNoDirectoryIndex = false;
		Normalizer.Form optionNormalize = null;

		int optionsLength = args.length - 2;
		for (int i = 0; i < optionsLength; i++) {
			switch (args[i]) {
			case "--music":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				optionMusic = args[i];
				break;
			case "--retina":
				optionRetina = true;
				break;
			case "--pretty":
				optionPretty = true;
				break;
			case "--full":
				optionFull = true;
				break;
			case "--debug":
				optionDebug = true;
				break;
			case "--unknownGenre":
				optionUnknownGenre = true;
				break;
			case "--noVariousArtists":
				optionNoVariousArtists = true;
				break;
			case "--noImages":
				optionNoImages = true;
				break;
			case "--noDirectoryIndex":
				optionNoDirectoryIndex = true;
				break;
			case "--xml":
				optionXML = true;
				break;
			case "--normalize":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				try {
					optionNormalize = Normalizer.Form.valueOf(args[i]);
				} catch (IllegalArgumentException e) {
					exitWithError(command, "invalid normalize form: " + args[i]);
				}
				break;
			default:
				if (args[i].startsWith("-")) {
					exitWithError(command, "unknown option: " + args[i]);
				} else {
					exitWithError(command, "invalid arguments");
				}				
			}
		}
		for (int i = optionsLength; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				exitWithError(command, "invalid arguments");
			}
		}

		final File inputFolder = new File(args[optionsLength]);
		if (!inputFolder.exists() || inputFolder.isFile()) {
			exitWithError(command, "input folder doesn't exist: " + inputFolder);
		}
		final File outputFolder = new File(args[optionsLength + 1]);
		if (!outputFolder.exists() && !outputFolder.mkdirs()) {
			exitWithError(command, "cannot create output folder " + outputFolder);
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), optionDebug ? Level.FINER : Level.FINE);

		LocalStrings localStrings = new LocalStrings(Locale.ENGLISH);
		File assetStoreFile = new File(outputFolder, ASSET_STORE);

		AssetLocator assetStoreAssetLocator = new SimpleAssetLocator(inputFolder, optionMusic, null); // no normalization
		AssetStore assetStore = new AssetStore(API_VERSION);
		if (!optionFull && assetStoreFile.exists()) {
			LOGGER.info("Loading Asset Store...");
			try (InputStream assetStoreInput = createInputStream(assetStoreFile)) {
				assetStore.load(assetStoreInput, assetStoreAssetLocator);
			} catch (Exception e) {
				LOGGER.warning("Failed to load asset store...");
				assetStore = new AssetStore(API_VERSION);
			}
		}

		AssetParser assetParser = new SimpleAssetParser();

		LOGGER.info("Parsing Music Libary...");
		Library library = new LibraryParser(assetParser).parse(inputFolder, assetStore);
		if (optionNoVariousArtists) { // remove "various artists" album artist (hack)
			library.getAlbumArtists().remove(null);
		}

		ResourceLocator resourceLocator = new SimpleResourceLocator(outputFolder, optionXML, optionNoImages);

		if (!optionNoImages) {
			LOGGER.info("Generating Images...");
			ImageFormatter formatter = new ImageFormatter(assetParser, optionRetina);
			formatter.formatImages(library, resourceLocator, assetStore);
		}

		ResponseFormatter<?> responseFormatter;
		if (optionXML) {
			responseFormatter = new ResponseFormatter.XML(API_VERSION, localStrings, optionNoDirectoryIndex, optionUnknownGenre, optionPretty);
		} else {
			responseFormatter = new ResponseFormatter.JSON(API_VERSION, localStrings, optionNoDirectoryIndex, optionUnknownGenre, optionPretty);
		}
		AssetLocator responseAssetLocator = new SimpleAssetLocator(inputFolder, optionMusic, optionNormalize);
		LOGGER.info("Generating JSON...");
		generateResponseFiles(library, outputFolder, responseFormatter, resourceLocator, responseAssetLocator);

		LOGGER.info("Saving Asset Store...");
		try (OutputStream assetStoreOutput = createOutputStream(assetStoreFile)) {
			assetStore.save(assetStoreOutput, assetStoreAssetLocator);
		} catch (Exception e) {
			LOGGER.warning("Failed to save asset store...");
			assetStoreFile.deleteOnExit();
		}

		LOGGER.info(String.format("Done (%d Albums).", library.getAlbums().size()));
	}
	
	private static InputStream createInputStream(File file) throws IOException {
		if (!file.exists()) {
			return null;
		}
		InputStream input = new FileInputStream(file);
		if (file.getName().endsWith(".gz")) {
			input = new GZIPInputStream(input);
		}
		return new BufferedInputStream(input);
	}

	private static OutputStream createOutputStream(File file) throws IOException {
		file.getParentFile().mkdirs();
		OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
		if (file.getName().endsWith(".gz")) {
			output = new GZIPOutputStream(output);
		}
		return output;
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountBuilder.class.getSimpleName(), args);
	}
}
