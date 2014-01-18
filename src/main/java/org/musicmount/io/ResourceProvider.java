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
	 * @param path
	 * @return new resource
	 */
	public Resource newResource(Path path);

	/**
	 * @param path
	 * @return new resource
	 */
	public Resource newResource(String first, String... more);

	/**
	 * @return absolute and normalized base directory
	 */
	public Resource getBaseDirectory();
	
	/**
	 * @param path
	 * @return <true> if directory
	 * @throws IOException
	 */
	public boolean isDirectory(Path path) throws IOException;
}
