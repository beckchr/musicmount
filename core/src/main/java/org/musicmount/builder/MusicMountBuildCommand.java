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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.io.server.dav.DAVResourceProvider;
import org.musicmount.io.server.smb.SMBResourceProvider;
import org.musicmount.util.LoggingUtil;

public class MusicMountBuildCommand {
	static final Logger LOGGER = Logger.getLogger(MusicMountBuildCommand.class.getName());

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

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <musicFolder> <mountFolder>", command));
		System.err.println();
		System.err.println("Generate static MusicMount site from music in <musicFolder> into <mountFolder>");
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
		System.err.println("       --grouping         use grouping tag to group album tracks");
		System.err.println("       --unknownGenre     report missing genre as 'Unknown'");
		System.err.println("       --noTrackIndex     do not generate a track index");
		System.err.println("       --noVariousArtists exclude 'Various Artists' from album artist index");
		System.err.println("       --directoryIndex   use 'path/' instead of 'path/index.ext'");
		System.err.println("       --full             full parse, don't use asset store");
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
	 * @param args options, inputFolder, outputFolder
	 * @throws Exception something went wrong...
	 */
	public static void execute(String command, String... args) throws Exception {
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
				builder.getConfig().setRetina(true);
				break;
			case "--pretty":
				builder.getConfig().setPretty(true);
				break;
			case "--full":
				builder.getConfig().setFull(true);
				break;
			case "--verbose":
				optionVerbose = true;
				break;
			case "--unknownGenre":
				builder.getConfig().setUnknownGenre(true);
				break;
			case "--noTrackIndex":
				builder.getConfig().setNoTrackIndex(true);
				break;
			case "--noVariousArtists":
				builder.getConfig().setNoVariousArtists(true);
				break;
			case "--noImages":
				builder.getConfig().setNoImages(true);
				break;
			case "--noDirectoryIndex": // deprecated
				break;
			case "--directoryIndex":
				builder.getConfig().setDirectoryIndex(true);
				break;
			case "--xml":
				builder.getConfig().setXml(true);
				break;
			case "--grouping":
				builder.getConfig().setGrouping(true);
				break;
			case "--normalize":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				try {
					builder.getConfig().setNormalizer(Normalizer.Form.valueOf(args[optionsLength]));
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
		LoggingUtil.configure(MusicMountBuildCommand.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);

		if (!"file".equals(musicFolder.getPath().toUri().getScheme()) || !"file".equals(mountFolder.getPath().toUri().getScheme())) {
			LOGGER.warning("Remote file system support is experimental/alpha!");
		}

		/**
		 * Run builder
		 */
		builder.build(musicFolder, mountFolder, optionMusic);
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountBuildCommand.class.getSimpleName(), args);
	}
}
