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
package org.musicmount.io.file;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import org.musicmount.io.Resource;
import org.musicmount.io.ResourceProvider;

public class FileResourceProvider implements ResourceProvider {
	private final FileSystem fileSystem;
	private final Resource baseDirectory;
	
	public FileResourceProvider() {
		this(FileSystems.getDefault(), System.getProperty("user.dir"));
	}
	
	public FileResourceProvider(String basePath) {
		this(FileSystems.getDefault(), basePath);
	}
	
	public FileResourceProvider(FileSystem fileSystem, String basePath) {
		this.fileSystem = fileSystem;
		this.baseDirectory = newResource(fileSystem.getPath(basePath).toAbsolutePath().normalize());
	}
	
	@Override
	public boolean isDirectory(Path path) {
		return Files.isDirectory(path);
	}
	
	@Override
	public Resource getBaseDirectory() {
		return baseDirectory;
	}
	
	@Override
	public Resource newResource(Path path) {
		if (!fileSystem.equals(path.getFileSystem())) {
			throw new IllegalArgumentException("Invalid path");
		}
		return new FileResource(this, path);
	}
	
	@Override
	public Resource newResource(String first, String... more) {
		return new FileResource(this, fileSystem.getPath(first, more));
	}
}
