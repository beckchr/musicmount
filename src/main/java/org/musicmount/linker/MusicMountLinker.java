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
package org.musicmount.linker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.util.LoggingUtil;

public class MusicMountLinker {
	static final Logger LOGGER = Logger.getLogger(MusicMountLinker.class.getName());

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder> <mount_folder>", command));
		System.err.println();
		System.err.println("Link MusicMount site in <mount_folder> to music from <music_folder>");
		System.err.println();
		System.err.println("         <music_folder>   target folder (containing the music library)");
		System.err.println("         <mount_folder>   source folder (to contain the generated site)");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music link name, default is 'music'");
		System.err.println("       --relativize       relativize path from <mount_folder> to <music_folder>");
		System.err.println("       --force            overwrite link if it already exists");
		System.err.println("       --verbose          more detailed console output");
		System.err.close();
		System.exit(1);	
	}	

	/**
	 * Launch HTTP Server
	 * @param args inputFolder, outputFolder
	 * @throws Exception
	 */
	public static void execute(String command, String[] args) throws Exception {
		String optionMusic = "music";
		boolean optionForce = false;
		boolean optionVerbose = false;
		boolean optionRelativize = false;

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
			case "--force":
				optionForce = true;
				break;
			case "--relativize":
				optionRelativize = true;
				break;
			case "--verbose":
				optionVerbose = true;
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
		case 1:
			exitWithError(command, "missing arguments");
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
		LoggingUtil.configure(MusicMountLinker.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);
		
		/*
		 * create symbolic link: <mount_folder>/<value of --music option> -> <music_folder>
		 */
		Path source = mountFolder.toPath().toAbsolutePath();
		Path link = source.resolve(optionMusic);
		Path target = musicFolder.toPath().toAbsolutePath();
		if (optionRelativize) {
			target = source.relativize(target);
		}
		LOGGER.info("Creating symbolic link: " + link + " -> " + target);
		if (Files.exists(link)) {
			if (Files.isSymbolicLink(link)) {
				if (optionForce) {
					try {
						Files.delete(link);
					} catch (Exception e) {
						exitWithError(command, "cannot delete existing link: " + e.getMessage());
					}
				} else {
					exitWithError(command, "link already exists, use option --force to overwrite");
				}
			} else {
				exitWithError(command, "link source already exists, won't delete/overwrite");
			}
		}
		try {
			Files.createSymbolicLink(link, target);
		} catch (UnsupportedOperationException e) {
			exitWithError(command, "file system does not support symbolic links: " + e.getMessage());
		} catch (Exception e) {
			exitWithError(command, "cannot create symbolic link: " + e.getMessage());
		}
		LOGGER.info("Done.");
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountLinker.class.getSimpleName(), args);
	}
}
