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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import org.musicmount.builder.impl.AssetLocator;
import org.musicmount.builder.impl.AssetParser;
import org.musicmount.builder.impl.AssetStore;
import org.musicmount.builder.impl.ImageFormatter;
import org.musicmount.builder.impl.LibraryParser;
import org.musicmount.builder.impl.LocalStrings;
import org.musicmount.builder.impl.ResourceLocator;
import org.musicmount.builder.impl.ResponseFormatter;
import org.musicmount.builder.impl.SimpleAssetLocator;
import org.musicmount.builder.impl.SimpleAssetParser;
import org.musicmount.builder.impl.SimpleResourceLocator;
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

		/*
		 * album artists
		 */
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating album artists...");
		}
		Map<Artist, Album> representativeAlbums = new HashMap<Artist, Album>();
		for (Artist artist : library.getAlbumArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for album artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Generating album artist index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist)))) {
			formatter.formatArtistIndex(library.getAlbumArtists().values(), ArtistType.AlbumArtist, output, resourceLocator, representativeAlbums);
		}

		/*
		 * artists
		 */
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating artists...");
		}
		representativeAlbums.clear();
		for (Artist artist : library.getTrackArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Generating artist index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getArtistIndexPath(ArtistType.TrackArtist)))) {
			formatter.formatArtistIndex(library.getTrackArtists().values(), ArtistType.TrackArtist, output, resourceLocator, representativeAlbums);
		}

		/*
		 * albums
		 */
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating albums...");
		}
		for (Album album : library.getAlbums()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album: " + album.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumPath(album)))) {
				formatter.formatAlbum(album, output, resourceLocator, assetLocator);
			}
		}
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Generating album index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getAlbumIndexPath()))) {
			formatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);
		}

		/*
		 * service index last
		 */
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Generating service index...");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getFile(resourceLocator.getServiceIndexPath()))) {
			formatter.formatServiceIndex(resourceLocator, output);
		}
	}
	
	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] [<music_folder>] <mount_folder>", command));
		System.err.println();
		System.err.println("Generate MusicMount site from music in <music_folder> into <mount_folder>");
		System.err.println();
		System.err.println("         <music_folder>   input folder, default is <mount_folder>/<value of --music option>");
		System.err.println("         <mount_folder>   output folder to contain the generated site");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music path prefix, default is 'music'");
		System.err.println("       --retina           double image resolution");
		System.err.println("       --full             full parse, don't use asset store");
		System.err.println("       --grouping         use grouping tag to group album tracks");
		System.err.println("       --unknownGenre     report missing genre as 'Unknown'");
		System.err.println("       --noVariousArtists exclude 'Various Artists' from album artist index");
		System.err.println("       --directoryIndex   use 'path/' instead of 'path/index.ext'");
		System.err.println("       --normalize <form> normalize asset paths, 'NFC'|'NFD' (experimental)");
		System.err.println("       --pretty           pretty-print JSON documents");
		System.err.println("       --verbose          more detailed console output");
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
		String optionMusic = "music";
		boolean optionRetina = false;
		boolean optionPretty = false;
		boolean optionFull = false;
		boolean optionVerbose = false;
		boolean optionNoImages = false;
		boolean optionXML = false;
		boolean optionGrouping = false;
		boolean optionUnknownGenre = false;
		boolean optionNoVariousArtists = false;
		boolean optionDirectoryIndex = false;
		Normalizer.Form optionNormalize = null;

		int optionsLength = 0;
		boolean optionsDone = false;
		while (optionsLength < args.length && !optionsDone) {
			switch (args[optionsLength]) {
			case "--music":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionMusic = args[optionsLength];
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
			case "--verbose":
				optionVerbose = true;
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
			case "--noDirectoryIndex": // deprecated
				break;
			case "--directoryIndex":
				optionDirectoryIndex = true;
				break;
			case "--xml":
				optionXML = true;
				break;
			case "--grouping":
				optionGrouping = true;
				break;
			case "--normalize":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				try {
					optionNormalize = Normalizer.Form.valueOf(args[optionsLength]);
				} catch (IllegalArgumentException e) {
					exitWithError(command, "invalid normalize form: " + args[optionsLength]);
				}
				break;
			default:
				if (args[optionsLength].startsWith("-")) {
					exitWithError(command, "unknown option: " + args[optionsLength]);
				} else {
					optionsDone = true;
				}				
			}
			if (!optionsDone) {
				optionsLength++;
			}
		}
		for (int i = optionsLength; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				exitWithError(command, "invalid arguments");
			}
		}
		
		File musicFolder = null;
		File mountFolder = null;
		switch (args.length - optionsLength) {
		case 0:
			exitWithError(command, "missing arguments");
			break;
		case 1:
			mountFolder = new File(args[optionsLength]);
			musicFolder = new File(mountFolder, optionMusic);
			break;
		case 2:
			musicFolder = new File(args[optionsLength]);
			mountFolder = new File(args[optionsLength + 1]);
			break;
		default:
			exitWithError(command, "bad arguments");
		}
		if (mountFolder.exists()) {
			if (!mountFolder.isDirectory()) {
				exitWithError(command, "mount folder is not a directory: " + mountFolder);
			}
		} else {
			if (!mountFolder.mkdirs()) {
				exitWithError(command, "cannot create mount folder " + mountFolder);
			}
		}
		if (musicFolder.exists()) {
			if (!musicFolder.isDirectory()) {
				exitWithError(command, "music folder is not a directory: " + musicFolder);
			}
		} else {			
			exitWithError(command, "music folder doesn't exist: " + musicFolder);
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);

		LocalStrings localStrings = new LocalStrings(Locale.ENGLISH);
		File assetStoreFile = new File(mountFolder, ASSET_STORE);

		AssetLocator assetStoreAssetLocator = new SimpleAssetLocator(musicFolder, null, null); // no prefix, no normalization
		AssetStore assetStore = new AssetStore(API_VERSION);
		boolean assetStoreLoaded = false;
		if (!optionFull && assetStoreFile.exists()) {
			LOGGER.info("Loading asset store...");
			try (InputStream assetStoreInput = createInputStream(assetStoreFile)) {
				assetStore.load(assetStoreInput, assetStoreAssetLocator);
				assetStoreLoaded = true;
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load asset store", e);
				assetStore = new AssetStore(API_VERSION);
			}
		}

		AssetParser assetParser = new SimpleAssetParser();

		LOGGER.info(assetStoreLoaded ? "Updating asset store..." : "Creating asset store...");
		assetStore.update(musicFolder, assetParser);

		LOGGER.info("Building music libary...");
		Library library = new LibraryParser().parse(assetStore.assets());
		if (optionNoVariousArtists) { // remove "various artists" album artist (hack)
			library.getAlbumArtists().remove(null);
		}
		Set<Album> changedAlbums = assetStore.sync(library.getAlbums());
		if (LOGGER.isLoggable(Level.FINE) && assetStoreLoaded) {
			LOGGER.fine(String.format("Number of albums changed: %d", changedAlbums.size()));
		}

		ResourceLocator resourceLocator = new SimpleResourceLocator(mountFolder, optionXML, optionNoImages);

		if (optionNoImages) {
			assetStore.setRetina(null);
		} else {
			LOGGER.info(assetStoreLoaded ? "Updating images..." : "Generating images...");
			ImageFormatter formatter = new ImageFormatter(assetParser, optionRetina);
			final boolean retinaChange = !Boolean.valueOf(optionRetina).equals(assetStore.getRetina());
			if (LOGGER.isLoggable(Level.FINE) && retinaChange && assetStoreLoaded) {
				LOGGER.fine(String.format("Retina state %s", assetStore.getRetina() == null ? "unknown" : "changed"));
			}
			formatter.formatImages(library, resourceLocator, retinaChange || optionFull ? new HashSet<>(library.getAlbums()) : changedAlbums);
			assetStore.setRetina(optionRetina);
		}

		ResponseFormatter<?> responseFormatter;
		if (optionXML) {
			responseFormatter = new ResponseFormatter.XML(API_VERSION, localStrings, optionDirectoryIndex, optionUnknownGenre, optionGrouping, optionPretty);
		} else {
			responseFormatter = new ResponseFormatter.JSON(API_VERSION, localStrings, optionDirectoryIndex, optionUnknownGenre, optionGrouping, optionPretty);
		}
		AssetLocator responseAssetLocator = new SimpleAssetLocator(musicFolder, optionMusic, optionNormalize);
		LOGGER.info("Generating JSON...");
		generateResponseFiles(library, mountFolder, responseFormatter, resourceLocator, responseAssetLocator);

		LOGGER.info("Saving asset store...");
		try (OutputStream assetStoreOutput = createOutputStream(assetStoreFile)) {
			assetStore.save(assetStoreOutput, assetStoreAssetLocator);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to save asset store", e);
			assetStoreFile.deleteOnExit();
		}

		LOGGER.info(String.format("Done (%d albums).", library.getAlbums().size()));
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
		File folder = file.getParentFile();
		if (!folder.exists() && !folder.mkdirs()) {
			throw new IOException("Could not create directory: " + folder.getAbsolutePath());
		}
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
