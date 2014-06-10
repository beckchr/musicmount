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
package org.musicmount.tester;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.BonjourService;
import org.musicmount.util.LoggingUtil;

public class MusicMountTestCommand {
	static final Logger LOGGER = Logger.getLogger(MusicMountTestCommand.class.getName());

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder> <mount_folder>", command));
		System.err.println();
		System.err.println("Launch MusicMount site in <mount_folder> with music from <music_folder>");
		System.err.println();
		System.err.println("         <music_folder>   input folder (containing the music library)");
		System.err.println("         <mount_folder>   output folder (to contain the generated site)");
		System.err.println();
		System.err.println("Folders must be local.");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>");
		System.err.println("       --port <port>      launch HTTP server on specified port (default 8080)");
		System.err.println("       --user <user>      login user");
		System.err.println("       --password <pass>  login password");
		System.err.println("       --bonjour          publish as bonjour service ('Test @ <hostName>')");
		System.err.println("       --verbose          more detailed console output");
		System.err.close();
		System.exit(1);
	}

	/**
	 * Launch HTTP Server
	 * @param command command name (e.g. "test")
	 * @param args options, musicFolder, mountFolder
	 * @throws Exception something went wrong...
	 */
	public static void execute(String command, String... args) throws Exception {
		String optionMusic = null;
		int optionPort = 8080;
		String optionUser = null;
		String optionPassword = null;
		boolean optionVerbose = false;
		boolean optionBonjour = false;

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
			case "--port":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionPort = Integer.parseInt(args[optionsLength]);
				break;
			case "--user":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionUser = args[optionsLength];
				break;
			case "--password":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionPassword = args[optionsLength];
				break;
			case "--bonjour":
				optionBonjour = true;
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

		FileResource musicFolder = null;
		FileResource mountFolder = null;
		switch (args.length - optionsLength) {
		case 0:
		case 1:
			exitWithError(command, "missing arguments");
			break;
		case 2:
			musicFolder = new FileResourceProvider(args[optionsLength]).getBaseDirectory();
			mountFolder = new FileResourceProvider(args[optionsLength + 1]).getBaseDirectory();
			break;
		default:
			exitWithError(command, "bad arguments");
		}
		if (mountFolder.exists()) {
			if (!mountFolder.isDirectory()) {
				exitWithError(command, "mount folder is not a directory: " + mountFolder);
			}
		} else {
			exitWithError(command, "mount folder doesn't exist: " + musicFolder);
		}
		if (musicFolder.exists()) {
			if (!musicFolder.isDirectory()) {
				exitWithError(command, "music folder is not a directory: " + musicFolder);
			}
		} else {
			exitWithError(command, "music folder doesn't exist: " + musicFolder);
		}
		if ((optionUser == null) != (optionPassword == null)) {
			exitWithError(command, String.format("either both or none of user/password must be given: %s/%s", optionUser, optionPassword));
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountTestCommand.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);
		
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

		MusicMountTester tester = new MusicMountTester();
		tester.start(musicFolder, mountFolder, optionMusic, optionPort, optionUser, optionPassword);

		/*
		 * Register Bonjour service
		 */
		if (optionBonjour) {
			LOGGER.info("Registering Bonjour service...");
			BonjourService bonjourService = null;
			try {
				bonjourService = new BonjourService(true);
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Failed to create Bonjour service", e);
			}
			if (bonjourService != null) {
				String host = tester.getHostName(bonjourService.getHostName());
				try {
					bonjourService.start(String.format("Test @ %s", host), tester.getSiteURL(host, optionPort, optionMusic), optionUser);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Failed to start Bonjour service", e);
					try {
						bonjourService.close();
					} catch (IOException e2) {
						LOGGER.log(Level.WARNING, "Failed to close Bonjour service", e2);
					} finally {
						bonjourService = null;
					}
				}
			}
		}
		
		LOGGER.info("Press CTRL-C to exit...");
		tester.await();
	}

	public static void main(String[] args) throws Exception {
		execute(MusicMountTestCommand.class.getSimpleName(), args);
	}
}
