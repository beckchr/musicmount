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
package org.musicmount.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

public interface Resource {
	public ResourceProvider getProvider();

	public Resource getParent();
	public Resource resolve(String path);
	
	public Path getPath();
	public String getName();

	public boolean isDirectory() throws IOException;
	public boolean exists() throws IOException;
	public long length() throws IOException;
	public long lastModified() throws IOException;
	
	public void delete() throws IOException;
	public void mkdirs() throws IOException;
	
	public InputStream getInputStream() throws IOException;
	public OutputStream getOutputStream() throws IOException;
	
	public DirectoryStream<Resource> newResourceDirectoryStream() throws IOException;
	public DirectoryStream<Resource> newResourceDirectoryStream(DirectoryStream.Filter<Path> filter) throws IOException;

}
