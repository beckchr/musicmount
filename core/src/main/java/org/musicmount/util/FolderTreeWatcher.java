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
package org.musicmount.util;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.file.FileResource;

public class FolderTreeWatcher implements Runnable {
	static final Logger LOGGER = Logger.getLogger(FolderTreeWatcher.class.getName());

	/**
	 * Delegate interface
	 */
	public interface Delegate {
		void pathAdded(Path path);
		void pathDeleted(Path path);
		void pathModified(Path path);
	}
	
	private final WatchService watcher;
	private final Map<WatchKey, Path> keys;
	private final Delegate delegate;

	/**
	 * Creates a WatchService and registers the given folder
	 */
	public FolderTreeWatcher(FileResource folder, Delegate delegate) throws IOException {
		this.delegate = delegate;
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<WatchKey, Path>();

		registerAll(folder.getPath());
	}
	

	/**
	 * Register the given directory
	 * @param dir
	 */
	private void register(Path dir) throws IOException {
		keys.put(dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), dir);
	}

	/**
	 * Register the given directory tree
	 * @param dir
	 */
	private void registerAll(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
				register(path);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Handle watch events
	 * @param key
	 */
	private void handle(WatchKey key) {
		Path dir = keys.get(key);
		if (dir == null) {
			LOGGER.fine("WatchKey not recognized: " + key.watchable());
			return;
		}
		
		for (WatchEvent<?> event : key.pollEvents()) {
			WatchEvent.Kind<?> kind = event.kind();

			// print out event
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer(String.format("%s: %s\n", kind.name(), event.context()));
			}

			if (kind != OVERFLOW) {
				// Context for directory entry event is the file name of entry
				Path path = dir.resolve((Path) event.context());

				// if directory is created, and watching recursively, then
				// register it and its sub-directories
				if (kind == ENTRY_CREATE) {
					delegate.pathAdded(path);
					try {
						if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
							registerAll(path);
						}
					} catch (IOException e) {
						LOGGER.log(Level.WARNING, "Could not register directory: " + path, e);
					}
				} else if (kind == ENTRY_DELETE) {
					delegate.pathDeleted(path);
				} else if (kind == ENTRY_MODIFY) {
					delegate.pathModified(path);
				}
			}
		}

		// reset key and remove from set if directory no longer accessible
		if (!key.reset()) {
			keys.remove(key);
		}
	}
	
	/**
	 * Process all events for keys queued to the watcher
	 */
	public void run() {
		try (Closeable closeable = watcher) {
			while (!Thread.interrupted() && !keys.isEmpty()) { // some directories are accessible
				// wait for key to be signaled
				try {
					handle(watcher.take());
				} catch (InterruptedException | ClosedWatchServiceException e) {
					break;
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not close watcher", e);
		}
	}
}
