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

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingProgressHandler implements ProgressHandler {
	private final Logger logger;
	private final Level level;

	public LoggingProgressHandler(Logger logger, Level level) {
		this.logger = logger;
		this.level = level;
	}

	@Override
	public void beginTask(int totalWork, String title) {
		if (logger.isLoggable(level)) {
			if (title != null) {
				logger.log(level, String.format("Progress: %s", title));
			}
		}
	}
	@Override
	public void progress(int workDone, String message) {
		if (logger.isLoggable(level)) {
			logger.log(level, String.format("Progress: %s", message));
		}
	}
	@Override
	public void endTask() {
	}
}
