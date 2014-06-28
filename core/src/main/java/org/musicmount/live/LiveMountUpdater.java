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
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.io.file.FileResource;
import org.musicmount.util.FolderTreeWatcher;
import org.musicmount.util.ProgressHandler;

public class LiveMountUpdater {
	protected static final Logger LOGGER = Logger.getLogger(LiveMountUpdater.class.getName());

	/*
	 * Timer task performing the updates.
	 * An update is triggered if
	 * (a) something changed since the last update and
	 * (b) there was no change since delayMillis
	 */
	class UpdateTask extends TimerTask {
		private final ExecutorService executor;
		private final Runnable command;

		private Future<?> future;
		private long updateChange = lastChange;

		UpdateTask(final FileResource musicFolder, final String musicPath, final LiveMountBuilder builder, final LiveMountServlet servlet) {
			this.executor = Executors.newSingleThreadExecutor();
			this.command = new Runnable() {
				@Override
				public void run() {
					LOGGER.info("Updating live mount...");
					try {
						servlet.setMount(builder.update(musicFolder, musicPath, ProgressHandler.NOOP));
						LOGGER.info("Done.");
					} catch (Exception e) {
						LOGGER.log(Level.WARNING, "Could not update live mount", e);
					}
				}
			};
		}
		
		@Override
		public void run() {
			if (future != null) {
				if (future.isDone()) {
					future = null;
				} else { // busy
					return;
				}
			}

			final long lastChange = getLastChange();
			if (lastChange > updateChange && System.currentTimeMillis() - lastChange >= getDelayMillis()) {
				future = executor.submit(command);
				updateChange = lastChange;
			}
		}
		
		@Override
		public boolean cancel() {
			executor.shutdownNow();
			return super.cancel();
		}
	}
	
	/*
	 * minimum period of time without further changes required to trigger an update after a change.
	 */
	private final long delayMillis;

	private long lastChange = 0;

	private Timer updateTimer;
	private Thread watcherThread;
	
	public LiveMountUpdater(long delayMillis) {
		this.delayMillis = delayMillis;
	}

	public void start(FileResource musicFolder, String musicPath, LiveMountBuilder builder, LiveMountServlet servlet) throws IOException {
		watcherThread = new Thread(new FolderTreeWatcher(musicFolder, new FolderTreeWatcher.Delegate() {
			@Override
			public void pathModified(Path path) {
				lastChange = System.currentTimeMillis();
			}
			@Override
			public void pathDeleted(Path path) {
				lastChange = System.currentTimeMillis();
			}
			@Override
			public void pathAdded(Path path) {
				lastChange = System.currentTimeMillis();
			}
		}));
		watcherThread.start();

		updateTimer = new Timer(true);
		long periodMillis = Math.max(1000L, delayMillis / 10); // at most once a second
		updateTimer.schedule(new UpdateTask(musicFolder, musicPath, builder, servlet), periodMillis, periodMillis);
	}
	
	long getLastChange() {
		return lastChange;
	}
	
	long getDelayMillis() {
		return delayMillis;
	}
	
	public boolean isStarted() {
		return watcherThread != null && watcherThread.isAlive();
	}
	
	public void stop() {
		if (updateTimer != null) {
			updateTimer.cancel();
			updateTimer = null;
		}
		if (watcherThread != null) {
			watcherThread.interrupt();
			watcherThread = null;
		}
	}
}
