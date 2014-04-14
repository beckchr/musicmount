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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
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
import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.io.server.dav.DAVResourceProvider;
import org.musicmount.io.server.smb.SMBResourceProvider;
import org.musicmount.util.LoggingUtil;
import org.musicmount.util.ProgressHandler;

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

	static final Map<String, String> userProvidedPasswords = new HashMap<>();
	
	static URI getServerURI(URI uri) throws URISyntaxException {
		if (uri.getUserInfo() != null && uri.getUserInfo().indexOf(":") < 0) {
			String password = null;
			if (userProvidedPasswords.containsKey(uri.getAuthority())) {
				password = userProvidedPasswords.get(uri.getAuthority());
			} else {
				char[] passwordChars = System.console().readPassword("%s's password:", uri.getAuthority());
				if (passwordChars != null && passwordChars.length > 0) {
					password = String.valueOf(passwordChars);
				}
				userProvidedPasswords.put(uri.getAuthority(), password);
			}
			if (password != null) {
				uri = new URI(uri.getScheme(), uri.getUserInfo() + ":" + password, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
			}
		}
		return uri;
	}

	static ResourceProvider getResourceProvider(String string) throws IOException, URISyntaxException {
		String uriString = string.trim();

		/*
		 * convert file separators to URI slashes
		 */
		uriString = uriString.replace(FileSystems.getDefault().getSeparator(), "/");

		uriString = uriString.replace(" ", "%20"); // TODO URI encoding

		/*
		 * make sure base ends with '/'and is absolute
		 */
		if (!uriString.endsWith("/")) {
			uriString += "/";
		}
		
		/*
		 * create URI and switch by scheme...
		 */
		URI uri = new URI(uriString);
		if (uri.isAbsolute() && uri.getScheme().length() > 1) { // beware windows drive letters, e.g. C:\foo
			switch (uri.getScheme()) {
			case "file":
				return new FileResourceProvider(Paths.get(uri).toString());
			case "http":
			case "https":
				return new DAVResourceProvider(getServerURI(uri));
			case "smb":
				return new SMBResourceProvider(getServerURI(uri));
			default:
				throw new IOException("unsupported scheme: " + uri.getScheme());
			}
		}

		/*
		 * assume simple file path
		 */
		return new FileResourceProvider(string);
	}

	/**
	 * Resolve base/path to resource.
	 * @param base base resource (URL or file path, may be <code>null</code>)
	 * @param path resource path, resolved relative to base.
	 * @return resource
	 * @throws IOException IO exception
	 * @throws URISyntaxException URI syntax exception
	 */
	public static Resource getResource(String base, String path) throws IOException, URISyntaxException {
		if (base == null) {
			return getResourceProvider(path).getBaseDirectory();
		}

		/*
		 * absolute base directory
		 */
		Resource baseDirectory = getResourceProvider(base).getBaseDirectory();
		
		/*
		 * replace file separator
		 */
		String fileSeparator = baseDirectory.getPath().getFileSystem().getSeparator();
		path = path.replace(FileSystems.getDefault().getSeparator(), fileSeparator);

		/*
		 * make path relative + directory
		 */
		while (path.startsWith(fileSeparator)) {
			path = path.substring(1);
		}
		if (!path.endsWith(fileSeparator)) {
			path += fileSeparator;
		}

		/*
		 * resolve (i.e. append) path and normalize
		 */
		return baseDirectory.getProvider().newResource(baseDirectory.getPath().resolve(path).normalize());
	}

	private boolean retina = false;
	private boolean pretty = false;
	private boolean full = false;
	private boolean noImages = false;
	private boolean xml = false;
	private boolean grouping = false;
	private boolean unknownGenre = false;
	private boolean noVariousArtists = false;
	private boolean directoryIndex = false;
	private Normalizer.Form normalizer = null;

	private ProgressHandler progressHandler = ProgressHandler.NOOP;

	private final int maxAssetThreads;
	private final int maxImageThreads;

	public MusicMountBuilder() {
		this(1, Integer.MAX_VALUE);
	}

	public MusicMountBuilder(int maxAssetThreads, int maxImageThreads) {
		this.maxAssetThreads = maxAssetThreads;
		this.maxImageThreads = maxImageThreads;
	}

	public ProgressHandler getProgressHandler() {
		return progressHandler;
	}
	public void setProgressHandler(ProgressHandler progressHandler) {
		this.progressHandler = progressHandler;
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

	private InputStream createInputStream(Resource file) throws IOException {
		if (!file.exists()) {
			return null;
		}
		InputStream input = file.getInputStream();
		if (file.getName().endsWith(".gz")) {
			input = new GZIPInputStream(input);
		}
		return new BufferedInputStream(input);
	}

	private OutputStream createOutputStream(Resource file) throws IOException {
		file.getParent().mkdirs();
		OutputStream output = new BufferedOutputStream(file.getOutputStream());
		if (file.getName().endsWith(".gz")) {
			output = new GZIPOutputStream(output);
		}
		return output;
	}

	public void build(Resource musicFolder, Resource mountFolder, String musicPath) throws Exception {
		Resource assetStoreFile = mountFolder.resolve(ASSET_STORE);

		AssetLocator assetStoreAssetLocator = new SimpleAssetLocator(musicFolder, null, null); // no prefix, no normalization
		AssetStore assetStore = new AssetStore(API_VERSION);
		boolean assetStoreLoaded = false;
		if (!full && assetStoreFile.exists()) {
			if (progressHandler != null) {
				progressHandler.beginTask(-1, "Loading asset store...");
			}
			try (InputStream assetStoreInput = createInputStream(assetStoreFile)) {
				assetStore.load(assetStoreInput, assetStoreAssetLocator);
				assetStoreLoaded = true;
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to load asset store", e);
				assetStore = new AssetStore(API_VERSION);
			}
			if (progressHandler != null) {
				progressHandler.endTask();
			}
		}

		AssetParser assetParser = new SimpleAssetParser();

		assetStore.update(musicFolder, assetParser, maxAssetThreads, progressHandler);

		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Building music libary...");
		}
		Library library = new LibraryParser().parse(assetStore.assets());
		if (noVariousArtists) { // remove "various artists" album artist (hack)
			library.getAlbumArtists().remove(null);
		}
		Set<Album> changedAlbums = assetStore.sync(library.getAlbums());
		if (assetStoreLoaded) {
			LOGGER.fine(String.format("Number of albums changed: %d", changedAlbums.size()));
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}

		if (noImages) {
			assetStore.setRetina(null);
		} else {
			ImageFormatter formatter = new ImageFormatter(assetParser, retina);
			final boolean retinaChange = !Boolean.valueOf(retina).equals(assetStore.getRetina());
			if (LOGGER.isLoggable(Level.FINE) && retinaChange && assetStoreLoaded) {
				LOGGER.fine(String.format("Retina state %s", assetStore.getRetina() == null ? "unknown" : "changed"));
			}
			ResourceLocator resourceLocator = new SimpleResourceLocator(mountFolder, xml, noImages);
			Set<Album> imageAlbums = retinaChange || full ? new HashSet<>(library.getAlbums()) : changedAlbums;
			formatter.formatImages(library, resourceLocator, imageAlbums, maxImageThreads, progressHandler);
			assetStore.setRetina(retina);
		}
		
		generateResponseFiles(library, musicFolder, mountFolder, musicPath);

		if (progressHandler != null) {
			progressHandler.beginTask(-1, "Saving asset store...");
		}
		try (OutputStream assetStoreOutput = createOutputStream(assetStoreFile)) {
			assetStore.save(assetStoreOutput, assetStoreAssetLocator);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to save asset store", e);
			assetStoreFile.delete();
		}
		if (progressHandler != null) {
			progressHandler.endTask();
		}

		LOGGER.info("Done.");
	}

	void generateResponseFiles(Library library, Resource musicFolder, Resource mountFolder, String musicPath) throws Exception {
		if (progressHandler != null) {
			progressHandler.beginTask(7, "Generating JSON...");
		}

		LocalStrings localStrings = new LocalStrings(Locale.ENGLISH);
		ResponseFormatter<?> formatter;
		if (xml) {
			formatter = new ResponseFormatter.XML(API_VERSION, localStrings, directoryIndex, unknownGenre, grouping, pretty);
		} else {
			formatter = new ResponseFormatter.JSON(API_VERSION, localStrings, directoryIndex, unknownGenre, grouping, pretty);
		}
		AssetLocator assetLocator = new SimpleAssetLocator(musicFolder, musicPath, normalizer);
		ResourceLocator resourceLocator = new SimpleResourceLocator(mountFolder, xml, noImages);

		int workDone = -1;
		
		/*
		 * album artists
		 */
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%4d album artists...", library.getAlbumArtists().size()));
		}
		Map<Artist, Album> representativeAlbums = new HashMap<Artist, Album>();
		for (Artist artist : library.getAlbumArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for album artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, "album artist index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getArtistIndexPath(ArtistType.AlbumArtist)))) {
			formatter.formatArtistIndex(library.getAlbumArtists().values(), ArtistType.AlbumArtist, output, resourceLocator, representativeAlbums);
		}

		/*
		 * artists
		 */
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%4d artists...", library.getTrackArtists().size()));
		}
		representativeAlbums.clear();
		for (Artist artist : library.getTrackArtists().values()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album collection for artist: " + artist.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumCollectionPath(artist)))) {
				representativeAlbums.put(artist, formatter.formatAlbumCollection(artist, output, resourceLocator));
			}
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, "artist index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getArtistIndexPath(ArtistType.TrackArtist)))) {
			formatter.formatArtistIndex(library.getTrackArtists().values(), ArtistType.TrackArtist, output, resourceLocator, representativeAlbums);
		}

		/*
		 * albums
		 */
		if (progressHandler != null) {
			progressHandler.progress(++workDone, String.format("%4d albums...", library.getAlbums().size()));
		}
		for (Album album : library.getAlbums()) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Generating album: " + album.getTitle());
			}
			try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumPath(album)))) {
				formatter.formatAlbum(album, output, resourceLocator, assetLocator);
			}
		}
		if (progressHandler != null) {
			progressHandler.progress(++workDone, "album index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getAlbumIndexPath()))) {
			formatter.formatAlbumIndex(library.getAlbums(), output, resourceLocator);
		}

		/*
		 * service index last
		 */
		if (progressHandler != null) {
			progressHandler.progress(++workDone, "service index");
		}
		try (OutputStream output = createOutputStream(resourceLocator.getResource(resourceLocator.getServiceIndexPath()))) {
			formatter.formatServiceIndex(resourceLocator, output);
		}

		if (progressHandler != null) {
			progressHandler.endTask();
		}
	}

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <musicFolder> <mountFolder>", command));
		System.err.println();
		System.err.println("Generate MusicMount site from music in <musicFolder> into <mountFolder>");
		System.err.println();
		System.err.println("         <music_folder>   input folder (containing the music library)");
		System.err.println("         <mount_folder>   output folder (to contain the generated site)");
		System.err.println();
		System.err.println("Folders may be local directory paths or smb|http|https URLs, e.g. smb://user:pass@host/path/");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>");
		System.err.println("       --base <folder>    base folder, <musicFolder> and <mountFolder> are relative to this folder");
		System.err.println("       --retina           double image resolution");
		System.err.println("       --full             full parse, don't use asset store");
		System.err.println("       --grouping         use grouping tag to group album tracks");
		System.err.println("       --unknownGenre     report missing genre as 'Unknown'");
		System.err.println("       --noVariousArtists exclude 'Various Artists' from album artist index");
		System.err.println("       --directoryIndex   use 'path/' instead of 'path/index.ext'");
		System.err.println("       --pretty           pretty-print JSON documents");
		System.err.println("       --verbose          more detailed console output");
//		System.err.println("       --normalize <form> normalize asset paths, 'NFC'|'NFD' (experimental)");
//		System.err.println("       --noImages         do not generate images");
//		System.err.println("       --xml              generate XML instead of JSON");
		System.err.close();
		System.exit(1);	
	}

	/**
	 * Build a MusicMount site.
	 * @param command command name (e.g. "build")
	 * @param args inputFolder, outputFolder, baseURL
	 * @throws Exception something went wrong...
	 */
	public static void execute(String command, String[] args) throws Exception {
		MusicMountBuilder builder = new MusicMountBuilder();

		String optionBase = null;
		String optionMusic = null;
		boolean optionVerbose = false;

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
			case "--base":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionBase = args[optionsLength];
				break;
			case "--retina":
				builder.setRetina(true);
				break;
			case "--pretty":
				builder.setPretty(true);
				break;
			case "--full":
				builder.setFull(true);
				break;
			case "--verbose":
				optionVerbose = true;
				break;
			case "--unknownGenre":
				builder.setUnknownGenre(true);
				break;
			case "--noVariousArtists":
				builder.setNoVariousArtists(true);
				break;
			case "--noImages":
				builder.setNoImages(true);
				break;
			case "--noDirectoryIndex": // deprecated
				break;
			case "--directoryIndex":
				builder.setDirectoryIndex(true);
				break;
			case "--xml":
				builder.setXml(true);
				break;
			case "--grouping":
				builder.setGrouping(true);
				break;
			case "--normalize":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				try {
					builder.setNormalizer(Normalizer.Form.valueOf(args[optionsLength]));
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
		
		Resource musicFolder = null;
		Resource mountFolder = null;
		
		switch (args.length - optionsLength) {
		case 0:
		case 1:
			exitWithError(command, "missing arguments");
			break;
		case 2:
			musicFolder = getResource(optionBase, args[optionsLength]);
			mountFolder = getResource(optionBase, args[optionsLength + 1]);
			if (optionMusic == null && musicFolder.getPath().getRoot().equals(mountFolder.getPath().getRoot())) {
				try { // calculate relative path from mountFolder to musicFolder
					optionMusic = mountFolder.getPath().relativize(musicFolder.getPath()).toString();
				} catch (IllegalArgumentException e) {
					// cannot relativize
				}
			}
			if (optionMusic == null) {
				exitWithError(command, "could not calculate music path as relative path from <mountFolder> to <musicFolder>, use --music <path>");
			}
			optionMusic = optionMusic.replace(FileSystems.getDefault().getSeparator(), "/");
			break;
		default:
			exitWithError(command, "bad arguments");
		}

		try {
			if (mountFolder.exists()) {
				if (!mountFolder.isDirectory()) {
					exitWithError(command, "mount folder is not a directory: " + mountFolder);
				}
			} else {
				exitWithError(command, "mount folder doesn't exist: " + mountFolder);
			}
		} catch (IOException e) {
			exitWithError(command, "cannot connect to mount folder \"" + mountFolder.getPath().toUri() + "\": " + e.getMessage());
		}

		try {
			if (musicFolder.exists()) {
				if (!musicFolder.isDirectory()) {
					exitWithError(command, "music folder is not a directory: " + musicFolder);
				}
			} else {			
				exitWithError(command, "music folder doesn't exist: " + musicFolder);
			}
		} catch (IOException e) {
			exitWithError(command, "cannot connect to music folder \"" + musicFolder.getPath().toUri() + "\": " + e.getMessage());
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountBuilder.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);

		LOGGER.info("Music folder: " + musicFolder.getPath().toUri());
		LOGGER.info("Mount folder: " + mountFolder.getPath().toUri());
		LOGGER.info("Music path  : " + optionMusic);

		if (!"file".equals(musicFolder.getPath().toUri().getScheme()) || !"file".equals(mountFolder.getPath().toUri().getScheme())) {
			LOGGER.warning("Remote file system support is experimental/alpha!");
		}

		builder.setProgressHandler(new ProgressHandler() {
			@Override
			public void beginTask(int totalWork, String title) {
				if (LOGGER.isLoggable(Level.FINE)) {
					if (title != null) {
						LOGGER.fine(String.format("Progress: %s", title));
					}
				}
			}
			@Override
			public void progress(int workDone, String message) {
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine(String.format("Progress: %s", message));
				}
			}
			@Override
			public void endTask() {
			}
		});

		/**
		 * Run builder
		 */
		builder.build(musicFolder, mountFolder, optionMusic);
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountBuilder.class.getSimpleName(), args);
	}
}
