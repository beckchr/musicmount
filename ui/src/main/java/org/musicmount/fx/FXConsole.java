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
package org.musicmount.fx;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.musicmount.util.LoggingUtil;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * Redirect system out/err to text area.
 */
public class FXConsole {
	private static int BUFFER_SIZE = 8192;
	private static int MAX_TEXT_LEN = 1024 * 1024;
	private static int FLUSH_INTERVAL = 200;
	
    private final TextArea textArea;
    private final StringBuffer flushed = new StringBuffer();
    private final Thread flushThread = new Thread(new Runnable() {
		@Override
		public void run() {
			while (running) {
				try {
					Thread.sleep(FLUSH_INTERVAL);
					appendFlushed();
				} catch (InterruptedException | IOException e) {
					e.printStackTrace();
				}
			}
		}
    });
    private final OutputStream output = new OutputStream() {
    	private final byte[] buffer = new byte[BUFFER_SIZE];

    	@Override
        public void write(int i) throws IOException {
            if (pos == BUFFER_SIZE) {
                flush();
            }
            buffer[pos++] = (byte)i;
        }
    	
    	public void write(byte[] b, int off, int len) throws IOException {
    		if (pos + len < BUFFER_SIZE) {
    			System.arraycopy(b, off, buffer, pos, len);
    			pos += len;
    		} else {
    			flush();
    			if (len < BUFFER_SIZE) {
        			System.arraycopy(b, off, buffer, 0, len);
    			} else {
    				FXConsole.this.flush(b, off, len);
    			}
    		}
    	}
    	
        @Override
        public void flush() throws IOException {
        	FXConsole.this.flush(buffer, 0, pos);
        	pos = 0;
        }
        
        @Override
        public void close() throws IOException {
        	flush();
        }
	};

    private int pos = 0;
    private boolean running = false;
    private PrintStream saveErr, saveOut;

    public FXConsole() {
		this(new TextArea());
	}
    
    public FXConsole(TextArea textArea) {
		this.textArea = textArea;
		this.saveErr = System.err;
		this.saveOut = System.out;
		
		flushThread.setDaemon(true);
	}
    
    TextArea getTextArea() {
    	return textArea;
    }
    
    public void start() {
		PrintStream printStream = new PrintStream(output, true) {
			@Override
			public void close() {
				super.close();
				stop();
			}
		};
		System.setErr(printStream);
		System.setOut(printStream);
		LoggingUtil.updateConsoleHandlers();
		running = true;
		flushThread.start();
    }
    
    public void stop() {
		System.setErr(saveErr);
		System.setOut(saveOut);
		LoggingUtil.updateConsoleHandlers();
		running = false;
		try {
			flushThread.join();
		} catch (InterruptedException e) {
		}
    }
    
	void flush(byte[] b, int off, int len) {
    	if (len > 0) {
    		synchronized (flushed) {
    			flushed.append(new String(b, off, len));
    		}
    	}
	}

	void appendFlushed() throws IOException {
		synchronized (flushed) {
			if (flushed.length() > 0) {
				final String s = flushed.toString();
				flushed.setLength(0);
				Platform.runLater(new Runnable() {
					public void run() {
						textArea.appendText(s);
						int textLength = textArea.getText().length();
						if (textLength > MAX_TEXT_LEN) {
							textArea.setText(textArea.getText(textLength - MAX_TEXT_LEN / 2, textLength));
						}
					}
				});
			}
		}
    }
}
