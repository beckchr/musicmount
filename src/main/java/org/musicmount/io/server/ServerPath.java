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
package org.musicmount.io.server;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ServerPath implements Path {
	private static <T> int findCommenPrefix(List<T> one, List<T> two) {
		int n = Math.min(one.size(), two.size());
		for (int i = 0; i < n; i++) {
			if (!one.get(i).equals(two.get(i))) {
				return i;
			}
		}
		return n;
	}

	private static boolean isDirectoryElement(String element) {
		return ".".equals(element) || "..".equals(element);
	}

	private final ServerFileSystem fileSystem;
	private final List<String> elems;
	private final boolean absolute;
	private final boolean directory;

	public ServerPath(ServerFileSystem fileSystem, String first, String... more) {
		this.fileSystem = fileSystem;
		this.absolute = first.startsWith(fileSystem.getSeparator());
		this.elems = new ArrayList<>();
		
		for (String sub : first.split(fileSystem.getSeparator())) {
			if (!sub.isEmpty()) {
				elems.add(sub);
			}
		}
		for (String next : more) {
			for (String sub : next.split(fileSystem.getSeparator())) {
				if (!sub.isEmpty()) {
					elems.add(sub);
				}
			}
		}
		if (elems.size() > 0 && isDirectoryElement(elems.get(elems.size() - 1))) {
			this.directory = true;
		} else {
			String last = more.length > 0 ? more[more.length - 1] : first;
			this.directory = last.endsWith(fileSystem.getSeparator());
		}
	}
	
	private ServerPath(ServerFileSystem fileSystem, boolean absolute, boolean directory, String... elems) {
		this(fileSystem, absolute, directory, Arrays.asList(elems));
	}

	private ServerPath(ServerFileSystem fileSystem, boolean absolute, boolean directory, List<String> elems) {
		this.fileSystem = fileSystem;
		this.elems = elems;
		this.absolute = absolute;
		this.directory = directory;
	}
	
	public boolean isDirectory() {
		return directory;
	}

	@Override
	public FileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return absolute;
	}

	@Override
	public Path getRoot() {
		return absolute ? new ServerPath(fileSystem, true, true, fileSystem.getSeparator()) : null;
	}

	@Override
	public Path getFileName() {
		if (elems.isEmpty()) {
			return null;
		}
		if (elems.size() == 1) {
			return absolute ? new ServerPath(fileSystem, false, directory, elems) : this;
		}
		return new ServerPath(fileSystem, false, directory, elems.get(elems.size() - 1));
	}

	@Override
	public ServerPath getParent() {
		if (elems.isEmpty() || !absolute && elems.size() == 1) {
			return null;
		}
		return new ServerPath(fileSystem, absolute, true, elems.subList(0, elems.size() - 1));
	}

	@Override
	public int getNameCount() {
		return elems.size();
	}

	@Override
	public Path getName(int index) {
		return new ServerPath(fileSystem, false, false, elems.get(index));
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		return new ServerPath(fileSystem, absolute && beginIndex == 0, directory, elems.subList(beginIndex, endIndex));
	}

	@Override
	public boolean startsWith(Path other) {
		if (absolute != other.isAbsolute()) {
			return false;
		}
		if (elems.size() < other.getNameCount()) {
			return false;
		}
		for (int i = 0; i < other.getNameCount(); i++) {
			if (!elems.get(i).equals(other.getName(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean startsWith(String other) {
		return startsWith(getFileSystem().getPath(other));
	}

	@Override
	public boolean endsWith(Path other) {
		if (elems.size() < other.getNameCount()) {
			return false;
		}
		for (int i = 0; i < other.getNameCount(); i++) {
			if (!elems.get(elems.size() - 1 - i).equals(other.getName(other.getNameCount() - 1 - i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean endsWith(String other) {
		return endsWith(getFileSystem().getPath(other));
	}

	@Override
	public Path normalize() {
		if (!elems.contains(".") && !elems.contains("..")) {
			return this;
		}
		List<String> normalizedElems = new ArrayList<>();
		for (String elem : elems) {
			switch (elem) {
			case "..":
				if (normalizedElems.size() > 0 && !normalizedElems.get(normalizedElems.size() - 1).equals("..")) {
					normalizedElems.remove(normalizedElems.size() - 1);
				} else {
					normalizedElems.add(elem);
				}
				break;
			case ".":
				break;
			default:
				normalizedElems.add(elem);
			}
		}
		return new ServerPath(fileSystem, absolute, directory, normalizedElems);
	}

	@Override
	public Path resolve(Path other) {
		ServerPath path = (ServerPath) other;

		if (path.isAbsolute()) {
			return path;
		}
		if (path.getNameCount() == 0) {
			return this;
		}

		List<String> resolvedElems = new ArrayList<>(elems);
		resolvedElems.addAll(((ServerPath) path).elems);
		return new ServerPath(fileSystem, absolute, path.directory, resolvedElems);
	}

	@Override
	public Path resolve(String other) {
		return resolve(new ServerPath(fileSystem, other));
	}

	@Override
	public Path resolveSibling(Path other) {
		if (other.isAbsolute()) {
			return other;
		}
		Path parent = getParent();
		if (parent == null) {
			return other;
		}

		return getParent().resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		return resolveSibling(new ServerPath(fileSystem, other));
	}

	@Override
	public Path relativize(Path other) {
		if (absolute != other.isAbsolute()) {
			return null;
		}
		if (elems.size() == 0 && !other.isAbsolute()) {
			return other;
		}
		if (other.startsWith(this)) {
			return other.subpath(elems.size(), other.getNameCount());
		}
		
		List<String> from = elems;
		List<String> to = ((ServerPath) other).elems;
		List<String> path = new ArrayList<>();

		int prefix = findCommenPrefix(from, to);
		for (int i = prefix; i < from.size(); i++) {
			path.add("..");
		}
		for (int i = prefix; i < to.size(); i++) {
			path.add(to.get(i));
		}
		return new ServerPath(fileSystem, false, ((ServerPath) other).directory, path);
	}

	@Override
	public URI toUri() {
		try {
			return fileSystem.getServerUri(this);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Path toAbsolutePath() {
		return absolute ? this : fileSystem.getBaseDirectory().resolve(this);
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		return toAbsolutePath().normalize();
	}
	
	ServerPath toDirectoryPath(boolean directory) {
		return directory == this.directory ? this : new ServerPath(fileSystem, absolute, directory, elems);
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {
			private int index = 0;

			@Override
			public boolean hasNext() {
				return index < elems.size();
			}
			@Override
			public Path next() {
				return getName(index++);
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int compareTo(Path other) {
		int minCount = Math.min(getNameCount(), other.getNameCount());
		for (int i = 0; i < minCount; i++) {
			int result = getName(i).toString().compareTo(other.getName(i).toString());
			if (result != 0) {
				return result;
			}
		}
		return Integer.valueOf(getNameCount()).compareTo(Integer.valueOf(other.getNameCount()));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (absolute) {
			builder.append(fileSystem.getSeparator());
			if (elems.isEmpty()) { // root
				return builder.toString();
			}
		}
		for (int i = 0; i < elems.size(); i++) {
			if (i > 0) {
				builder.append(fileSystem.getSeparator());
			}
			builder.append(elems.get(i));
		}
		if (directory && (elems.size() == 0 || !isDirectoryElement(elems.get(elems.size() - 1)))) { // do not append '/' to '.' or '..'
			builder.append(fileSystem.getSeparator());
		}
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (absolute ? 1231 : 1237);
		result = prime * result + ((elems == null) ? 0 : elems.hashCode());
		result = prime * result + ((fileSystem == null) ? 0 : fileSystem.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ServerPath other = (ServerPath) obj;
		if (absolute != other.absolute) {
			return false;
		}
		if (elems == null) {
			if (other.elems != null) {
				return false;
			}
		} else if (!elems.equals(other.elems)) {
			return false;
		}
		if (fileSystem == null) {
			if (other.fileSystem != null) {
				return false;
			}
		} else if (!fileSystem.equals(other.fileSystem)) {
			return false;
		}
		return true;
	}
}