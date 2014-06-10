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
package org.musicmount.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/**
 * MusicMount Bonjour service
 */
public class BonjourService implements Closeable {
	private static final String SERVICE_TYPE = "_musicmount._tcp.local.";
	private static final String SERVICE_NAME = "MusicMount";
	
	private final JmDNS jmDNS;

	public BonjourService(boolean closeOnShutdown) throws IOException {
		jmDNS = JmDNS.create();
		if (closeOnShutdown) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						BonjourService.this.close();
					} catch (IOException e) {
						// ignore
					}
				}
			});
		}
	}
	
	public String getHostName() {
		return jmDNS.getHostName();
	}

	private void start(int port, Map<String, String> properties) throws IOException {
		jmDNS.registerService(ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, port, 0, 0, properties));
	}

	/**
	 * Register service.
	 * @param name name property
	 * @param url url property
	 * @param user user property
	 * @throws IOException
	 */
	public void start(String name, URL url, String user) throws IOException {
		Map<String, String> properties = new HashMap<>();
		if (name != null) {
			properties.put("name", name);
		}
		if (url != null) {
			properties.put("url", url.toString());
		}
		if (user != null) {
			properties.put("user", user);
		}
		start(url.getPort() > 0 ? url.getPort() : url.getDefaultPort(), properties);
	}

	/**
	 * Register service.
	 * @param name name property
	 * @param port service port
	 * @param path path property
	 * @param user user property
	 * @throws IOException
	 */
	public void start(String name, int port, String path, String user) throws IOException {
		Map<String, String> properties = new HashMap<>();
		if (name != null) {
			properties.put("name", name);
		}
		if (path != null) {
			properties.put("path", path);
		}
		if (user != null) {
			properties.put("user", user);
		}
		start(port, properties);
	}

	@Override
	public void close() throws IOException {
		jmDNS.close();
	}
}
