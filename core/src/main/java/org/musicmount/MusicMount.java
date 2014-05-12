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
package org.musicmount;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.builder.MusicMountBuildCommand;
import org.musicmount.linker.MusicMountLinkCommand;
import org.musicmount.tester.MusicMountTestCommand;
import org.musicmount.util.LoggingUtil;

public class MusicMount {
	static final Logger LOGGER = Logger.getLogger(MusicMount.class.getName());

	/**
	 * Configure logging
	 */
	static {
		LoggingUtil.configure(MusicMount.class.getPackage().getName(), Level.FINE);
	}

	static void exitWithError(String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println("Usage: MusicMount <command> ...");
		System.err.println();
		System.err.println("Execute MusicMount command");
		System.err.println();
		System.err.println("Commands:");
		System.err.println("       build      generate MusicMount site");
		System.err.println("       test       launch MusicMount test server");
		System.err.close();
		System.exit(1);	
	}

	public static void main(String[] args) throws Exception {
		String version = MusicMount.class.getPackage().getImplementationVersion();
		LOGGER.info("version " + (version != null ? version : "<unknown>") + " (java version " + System.getProperty("java.version") + ")");
		if (args.length == 0) {
			exitWithError("missing arguments");
		}

		String command = args[0];

		String executeCommand = "MusicMount " + command;
		String[] executeArgs = new String[args.length - 1]; 
		System.arraycopy(args, 1, executeArgs, 0, executeArgs.length);

		switch (command) {
		case "link":
			MusicMountLinkCommand.execute(executeCommand, executeArgs);
			break;
		case "build":
			MusicMountBuildCommand.execute(executeCommand, executeArgs);
			break;
		case "test":
			MusicMountTestCommand.execute(executeCommand, executeArgs);
			break;
		default:
			exitWithError("unknown command: " + command);
		}
	}
}
