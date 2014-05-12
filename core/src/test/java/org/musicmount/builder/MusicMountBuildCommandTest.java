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
package org.musicmount.builder;

import java.io.File;
import java.net.URL;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/*
 * TODO test doesn't assert anything
 */
public class MusicMountBuildCommandTest {
	@Rule
	public TemporaryFolder outputFolder = new TemporaryFolder();
	URL inputFolder = getClass().getResource("/sample-library");

	@Test
	public void testMain() throws Exception {
		String input = new File(inputFolder.toURI()).getAbsolutePath();
		String output = outputFolder.getRoot().getAbsolutePath();
		MusicMountBuildCommand.execute("TestCommand", "--pretty", "--full", input, output);
		MusicMountBuildCommand.execute("TestCommand", "--pretty", input, output); // use asset store
	}
}
