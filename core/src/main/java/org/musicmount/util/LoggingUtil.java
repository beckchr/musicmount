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

import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggingUtil {
	/**
	 * Setup simple console logging
	 * @param loggerName logger name
	 * @param level log level
	 */
	public static void configure(String loggerName, Level level) {
	    Handler handler = new ConsoleHandler();
	    handler.setLevel(level);
	    handler.setFormatter(new Formatter() {
			final MessageFormat messageFormat = new MessageFormat("{0}{1,date,HH:mm:ss} {2} - {3}{4}\n");
			@Override
			public synchronized String format(LogRecord record) {
				Object[] arguments = new Object[] {
					String.format("%-8s", record.getLevel()),
					new Date(record.getMillis()),
					record.getLoggerName() == null ? "<Unknown Logger>" : record.getLoggerName().substring(record.getLoggerName().lastIndexOf('.') + 1),
					record.getMessage() == null ? "<No Message>" : record.getMessage(),
					record.getThrown() == null ? "" : ", " + record.getThrown()
				};
				return messageFormat.format(arguments);
			}	
		});

	    Logger logger = Logger.getLogger(loggerName);
	    logger.setLevel(level);
	    for (Handler hdlr : logger.getHandlers()) {
	    	if (hdlr instanceof ConsoleHandler) {
	    		logger.removeHandler(hdlr);
	    	}
	    }
	    logger.addHandler(handler);
	    logger.setUseParentHandlers(false);
	}
}
