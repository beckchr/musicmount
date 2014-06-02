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

import java.io.File;

import javax.servlet.Servlet;

public interface MusicMountServer {
	interface AccessLog {
		interface Entry {
			long getRequestTimestamp();
			String getRequestURI();
			String getRequestMethod();
			int getResponseStatus();
			long getResponseTimestamp();
			String getResponseHeader(String header);
		}
		void log(Entry entry);
	}
	
	public static class FolderContext {
		private final String path;
		private final File folder;

		public FolderContext(String path, File folder) {
			this.path = path;
			this.folder = folder;
		}
		public String getPath() {
			return path;
		}		
		public File getFolder() {
			return folder;
		}
	}
	
	public static class MountContext {
		private final String path;
		private final Servlet servlet;

		public MountContext(String path, Servlet servlet) {
			this.path = path;
			this.servlet = servlet;
		}
		public String getPath() {
			return path;
		}		
		public Servlet getServlet() {
			return servlet;
		}
	}
	
	public void start(FolderContext music, FolderContext mount, int port, String user, String password) throws Exception;
	public void start(FolderContext music, MountContext mount, int port, String user, String password) throws Exception;
	public void await();
	public boolean isStarted();
	public void stop() throws Exception;
}
