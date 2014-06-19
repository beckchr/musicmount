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
package org.musicmount.builder.impl;

import java.io.IOException;
import java.nio.file.Files;

import org.musicmount.io.Resource;
import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;

public class AssetStoreRepository {
	/**
	 * Get user asset repository folder, <code>~/.musicmount/</code>
	 * @return folder or <code>null</code>
	 */
	public static FileResource getUserAssetStoreRepository() {
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			return new FileResourceProvider(userHome).getBaseDirectory().resolve(".musicmount");
		} else {
			return null;
		}
	}
	
	/**
	 * Get asset store file for given music folder.
	 * @param repository
	 * @param musicFolder
	 * @return resource
	 */
	public static Resource getAssetStoreResource(Resource repository, Resource musicFolder) {
		if (repository != null && musicFolder != null) {
			return repository.resolve(String.format("musicmount-%08x.gz", musicFolder.getPath().toUri().toString().hashCode()));
		} else {
			return null;
		}
	}
	
	/**
	 * Create temporary folder.
	 * @return folder or <code>null</code>
	 */
	public static FileResource createTemporaryAssetStoreRepository() {
		String tempFolder = null;
		try {
			tempFolder = Files.createTempDirectory("musicmount-").toFile().getAbsolutePath();
			return new FileResourceProvider(tempFolder).getBaseDirectory();
		} catch (IOException e) {
			return null;
		}
	}
}
