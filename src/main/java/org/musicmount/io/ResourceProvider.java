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
import java.nio.file.Path;

public interface ResourceProvider {
	
	/**
	 * Create resource
	 * @param path resource path
	 * @return new resource
	 */
	public Resource newResource(Path path);

	/**
	 * Create resource
	 * @param first path
	 * @param more more paths
	 * @return new resource
	 */
	public Resource newResource(String first, String... more);

	/**
	 * Get base directory
	 * @return absolute and normalized base directory
	 */
	public Resource getBaseDirectory();
	
	/**
	 * Test whether resource is directory
	 * @param path resource path
	 * @return <code>true</code> if directory
	 * @throws IOException IO exception
	 */
	public boolean isDirectory(Path path) throws IOException;
}
