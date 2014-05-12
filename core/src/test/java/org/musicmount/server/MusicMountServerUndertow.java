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

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.MimeMappings;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.musicmount.util.LoggingUtil;

/**
 * Experimental!
 * 
 * mount folder OK, music doesn't work... undertow does not support range requests, yet.
 */
public class MusicMountServerUndertow implements MusicMountServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountServerUndertow.class.getName());

	static {
		LoggingUtil.configure("io.undertoe", Level.INFO);
	}
	
	Undertow undertow;
	
	@Override
	public void start(FolderContext music, FolderContext mount, int port, String user, String password) throws Exception {
		final ResourceHandler musicResourceHandler = new ResourceHandler();
		musicResourceHandler.setResourceManager(new FileResourceManager(music.getFolder(), 0));
		MimeMappings.Builder musicMimeMappingsBuilder = MimeMappings.builder(true);
		musicMimeMappingsBuilder.addMapping("mp3", "audio/mpeg");
		musicMimeMappingsBuilder.addMapping("m4a", "audio/mp4");
		musicResourceHandler.setMimeMappings(musicMimeMappingsBuilder.build());
		
		final ResourceHandler mountResourceHandler = new ResourceHandler();
		mountResourceHandler.setResourceManager(new FileResourceManager(mount.getFolder(), 0));
		mountResourceHandler.addWelcomeFiles("index.json");
		MimeMappings.Builder mountMimeMappingsBuilder = MimeMappings.builder(false);
		mountMimeMappingsBuilder.addMapping("json", "text/json");
		mountResourceHandler.setMimeMappings(mountMimeMappingsBuilder.build());

		final PathHandler pathHandler = new PathHandler(mountResourceHandler);
		pathHandler.addPrefixPath(music.getPath(), musicResourceHandler);
		pathHandler.addPrefixPath(mount.getPath(), mountResourceHandler);
		
		HttpHandler handler = new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				System.out.println(exchange.getRequestPath());
				pathHandler.handleRequest(exchange);
			}
		};
		
		undertow = Undertow.builder().addHttpListener(port, null).setHandler(handler).build();
		undertow.start();
	}
	
	@Override
	public void await() {
		// TODO Auto-generated method stub		
	}
	
	@Override
	public void stop() throws Exception {
		undertow.stop();
		undertow = null;
	}
	
	@Override
	public boolean isStarted() {
		return undertow != null;
	}
}
