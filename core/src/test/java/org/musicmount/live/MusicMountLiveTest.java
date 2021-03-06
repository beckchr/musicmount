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
package org.musicmount.live;

import java.net.MalformedURLException;

import org.junit.Assert;
import org.junit.Test;

public class MusicMountLiveTest {
	MusicMountLive server = new MusicMountLive();

	@Test
	public void testGetSiteURL() throws MalformedURLException {
		Assert.assertEquals("http://<hostName>:1234/musicmount/", server.getSiteURL("<hostName>", 1234).toString());
	}
}
