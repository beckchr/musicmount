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
package org.musicmount.server;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.MimeMappings;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.util.LoggingUtil;

/**
 * mount folder OK, music doesn't work... undertow does not support range requests, yet.
 */
public class MusicMountUndertowServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountUndertowServer.class.getName());

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
		System.err.println("       --verbose          more detailed console output");
		System.err.close();
		System.exit(1);
	}

	public static void startServer(String host, int port, File mountBase, File musicBase, String musicPath, final String user, final String password) throws Exception {

		final ResourceHandler musicResourceHandler = new ResourceHandler();
		musicResourceHandler.setResourceManager(new FileResourceManager(musicBase, 0));
		MimeMappings.Builder musicMimeMappingsBuilder = MimeMappings.builder(true);
		musicMimeMappingsBuilder.addMapping("mp3", "audio/mpeg");
		musicMimeMappingsBuilder.addMapping("m4a", "audio/mp4");
		musicResourceHandler.setMimeMappings(musicMimeMappingsBuilder.build());
		
		final ResourceHandler mountResourceHandler = new ResourceHandler();
		mountResourceHandler.setResourceManager(new FileResourceManager(mountBase, 0));
		mountResourceHandler.addWelcomeFiles("index.json");
		MimeMappings.Builder mountMimeMappingsBuilder = MimeMappings.builder(false);
		mountMimeMappingsBuilder.addMapping("json", "text/json");
		mountResourceHandler.setMimeMappings(mountMimeMappingsBuilder.build());

		final PathHandler pathHandler = new PathHandler(mountResourceHandler);
		pathHandler.addPrefixPath("/musicmount/music", musicResourceHandler); // TODO test only
		pathHandler.addPrefixPath("/musicmount", mountResourceHandler);
		
		HttpHandler handler = new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				System.out.println(exchange.getRequestPath());
				pathHandler.handleRequest(exchange);
			}
		};
		
		Undertow server = Undertow.builder().addHttpListener(port, host).setHandler(handler).build();
		server.start();
	}

	/**
	 * Launch HTTP Server
	 * @param args inputFolder, outputFolder
	 * @throws Exception
	 */
	public static void execute(String command, String[] args) throws Exception {
		String optionMusic = null;
		int optionPort = 8080;
		String optionUser = null;
		String optionPassword = null;
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
		LoggingUtil.configure(MusicMountUndertowServer.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);
		LoggingUtil.configure("io.undertoe", Level.INFO);
		
		if (optionMusic == null) {
			Path mountFolderPath = mountFolder.toPath().toAbsolutePath().normalize();
			Path musicFolderPath = musicFolder.toPath().toAbsolutePath().normalize();
			optionMusic = mountFolderPath.relativize(musicFolderPath).toString();
		}
		optionMusic = optionMusic.replace(FileSystems.getDefault().getSeparator(), "/");

		LOGGER.info("Music folder: " + musicFolder.getPath());
		LOGGER.info("Mount folder: " + mountFolder.getPath());
		LOGGER.info("Music path  : " + optionMusic);
		LOGGER.info("");

		String hostname = InetAddress.getLoopbackAddress().getHostName();
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			LOGGER.log(Level.WARNING, "Could not determine local host name, showing loopback name", e);
		}

		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: http://%s:%d/musicmount/", hostname, optionPort));
		if (optionUser != null) {
			LOGGER.info(String.format("User: %s", optionUser));
			LOGGER.info(String.format("Pass: %s", optionPassword));
		}
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Starting Server..."));
		LOGGER.info("Press CTRL-C to exit...");
		startServer(hostname, optionPort, mountFolder, musicFolder, optionMusic, optionUser, optionPassword);
	}

	public static void main(String[] args) throws Exception {
		execute(MusicMountUndertowServer.class.getSimpleName(), args);
	}
}
