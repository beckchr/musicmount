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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.BonjourService;
import org.musicmount.util.LoggingUtil;

public class MusicMountLiveCommand {
	static final Logger LOGGER = Logger.getLogger(MusicMountLiveCommand.class.getName());

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder>", command));
		System.err.println();
		System.err.println("Launch in-memory MusicMount server from music in <music_folder>");
		System.err.println();
		System.err.println("         <music_folder>   input folder (containing the music library)");
		System.err.println();
		System.err.println("Folders must be local.");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --retina           double image resolution");
		System.err.println("       --grouping         use grouping tag to group album tracks");
		System.err.println("       --unknownGenre     report missing genre as 'Unknown'");
		System.err.println("       --noTrackIndex     do not generate a track index");
		System.err.println("       --noVariousArtists exclude 'Various Artists' from album artist index");
		System.err.println("       --port <port>      launch HTTP server on specified port (default 8080)");
		System.err.println("       --user <user>      login user");
		System.err.println("       --password <pass>  login password");
		System.err.println("       --bonjour          publish as bonjour service ('Live @ <hostName>')");
		System.err.println("       --full             full parse, don't use asset store");
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
		LiveMountBuilder builder = new LiveMountBuilder();
		
		int optionPort = 8080;
		String optionUser = null;
		String optionPassword = null;
		boolean optionVerbose = false;
		boolean optionBonjour = false;

		int optionsLength = 0;
		boolean optionsDone = false;
		while (optionsLength < args.length && !optionsDone) {
			switch (args[optionsLength]) {
			case "--retina":
				builder.getConfig().setRetina(true);
				break;
			case "--grouping":
				builder.getConfig().setGrouping(true);
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
			case "--full":
				builder.getConfig().setFull(true);
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
		switch (args.length - optionsLength) {
		case 1:
			musicFolder = new FileResourceProvider(args[optionsLength]).getBaseDirectory();
			break;
		default:
			exitWithError(command, "bad arguments");
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
		
		/*
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountLiveCommand.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);

		/*
		 * Start server
		 */
		MusicMountLive live = new MusicMountLive();
		try {
			live.start(musicFolder, builder, optionPort, optionUser, optionPassword);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Could not start server", e);
//			e.printStackTrace();
			System.exit(1);
		}

		/*
		 * Register Bonjour service
		 */
		if (optionBonjour) {
			LOGGER.info("Starting Bonjour service...");
			BonjourService bonjourService = null;
			try {
				bonjourService = new BonjourService(true);
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Failed to create Bonjour service", e);
			}
			if (bonjourService != null) {
				String host = live.getHostName(bonjourService.getHostName());
				try {
					bonjourService.start(String.format("Live @ %s", host), live.getSiteURL(host, optionPort), optionUser);
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Failed to start Bonjour service", e);
					try {
						bonjourService.stop();
					} catch (IOException e2) {
						LOGGER.log(Level.WARNING, "Failed to stop Bonjour service", e2);
					}
				}
			}
		}
		
		LOGGER.info("Press CTRL-C to exit...");
		live.await();
	}

	public static void main(String[] args) throws Exception {
		execute(MusicMountLiveCommand.class.getSimpleName(), args);
	}
}
