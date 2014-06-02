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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.GzipHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.musicmount.util.LoggingUtil;

public class MusicMountServerJetty implements MusicMountServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountServerJetty.class.getName());

	static {
		System.setProperty("embedded.jetty.daemon", "true");
		LoggingUtil.configure("org.eclipse.jetty", Level.INFO);
	}

	class ConsoleRequestLog extends AbstractLifeCycle implements RequestLog {
		@Override
		public void log(final Request request, final Response response) {
			if (accessLog != null) {
				final long responseTimestamp = System.currentTimeMillis();
				accessLog.log(new AccessLog.Entry() {
					@Override
					public long getResponseTimestamp() {
						return responseTimestamp;
					}
					@Override
					public int getResponseStatus() {
						return response.getStatus();
					}
					@Override
					public String getResponseHeader(String header) {
						return response.getHeader(header);
					}
					@Override
					public String getRequestURI() {
						return request.getRequestURI();
					}
					@Override
					public long getRequestTimestamp() {
						return request.getTimeStamp();
					}
					@Override
					public String getRequestMethod() {
						return request.getMethod();
					}
				});
			}
		}
	}

    static final SecurityHandler basicAuthentication(String realm, String username, String password) {
    	HashLoginService loginService = new HashLoginService();
        loginService.setName(realm);
        loginService.putUser(username, Credential.getCredential(password), new String[]{"user"});
        
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);
         
        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");
        
        ConstraintSecurityHandler constraintSecurityHandler = new ConstraintSecurityHandler();
        constraintSecurityHandler.setAuthenticator(new BasicAuthenticator());
        constraintSecurityHandler.setRealmName(realm);
        constraintSecurityHandler.addConstraintMapping(constraintMapping);
        constraintSecurityHandler.setLoginService(loginService);
        
        return constraintSecurityHandler;    	
    }

	private Server server;
	private final AccessLog accessLog;
	
	public MusicMountServerJetty(AccessLog accessLog) {
		this.accessLog = accessLog;
	}
		
	@Override
	public void start(FolderContext music, FolderContext mount, int port, String user, String password) throws Exception {
		ServletContextHandler mountContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        mountContext.setContextPath(mount.getPath());
        mountContext.setSecurityHandler(user == null ? null : basicAuthentication("MusicMount", user, password));
        mountContext.setBaseResource(Resource.newResource(mount.getFolder()));
        mountContext.setWelcomeFiles(new String[] { "index.json" });
        MimeTypes mountTypes = new MimeTypes();
        mountTypes.addMimeMapping("json", MimeTypes.TEXT_JSON_UTF_8);
        mountContext.setMimeTypes(mountTypes);
        ServletHolder mountServlet = new ServletHolder(new DefaultServlet());
        mountServlet.setInitParameter("dirAllowed", "false");
        mountContext.addServlet(mountServlet, "/*");

        ServletContextHandler musicContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        musicContext.setContextPath(music.getPath());
        musicContext.setSecurityHandler(user == null ? null : basicAuthentication("MusicMount", user, password));
        musicContext.setBaseResource(Resource.newResource(music.getFolder()));
        MimeTypes musicTypes = new MimeTypes();
        musicTypes.addMimeMapping("m4a", "audio/mp4");
        musicTypes.addMimeMapping("mp3", "audio/mpeg");
        musicContext.setMimeTypes(musicTypes);
        ServletHolder musicServlet = new ServletHolder(new DefaultServlet());
        musicServlet.setInitParameter("dirAllowed", "false");
        musicContext.addServlet(musicServlet, "/*");

        GzipHandler gzipMountContext = new GzipHandler();
        gzipMountContext.setMimeTypes(MimeTypes.TEXT_JSON);
        gzipMountContext.setHandler(mountContext);

        ContextHandlerCollection contexHandlers = new ContextHandlerCollection();
        contexHandlers.setHandlers(new Handler[] { gzipMountContext, musicContext });
        
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(new ConsoleRequestLog());   

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[]{ contexHandlers, new DefaultHandler(), requestLogHandler });

        server = new Server(port);
        server.setHandler(handlers);
        server.setGracefulShutdown(1000);
        server.setStopAtShutdown(true);
        server.start();
	}
	
	@Override
	public void start(FolderContext music, MountContext mount, int port, String user, String password) throws Exception {
		ServletContextHandler mountContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        mountContext.setContextPath(mount.getPath());
        mountContext.setSecurityHandler(user == null ? null : basicAuthentication("MusicMount", user, password));
        ServletHolder mountServlet = new ServletHolder(mount.getServlet());
        mountContext.addServlet(mountServlet, "/*");

        ServletContextHandler musicContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        musicContext.setContextPath(music.getPath());
        musicContext.setSecurityHandler(user == null ? null : basicAuthentication("MusicMount", user, password));
        musicContext.setBaseResource(Resource.newResource(music.getFolder()));
        MimeTypes musicTypes = new MimeTypes();
        musicTypes.addMimeMapping("m4a", "audio/mp4");
        musicTypes.addMimeMapping("mp3", "audio/mpeg");
        musicContext.setMimeTypes(musicTypes);
        ServletHolder musicServlet = new ServletHolder(new DefaultServlet());
        musicServlet.setInitParameter("dirAllowed", "false");
        musicContext.addServlet(musicServlet, "/*");

        GzipHandler gzipMountContext = new GzipHandler();
        gzipMountContext.setMimeTypes(MimeTypes.TEXT_JSON);
        gzipMountContext.setHandler(mountContext);

        ContextHandlerCollection contexHandlers = new ContextHandlerCollection();
        contexHandlers.setHandlers(new Handler[] { gzipMountContext, musicContext });
        
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(new ConsoleRequestLog());   

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[]{ contexHandlers, new DefaultHandler(), requestLogHandler });

        server = new Server(port);
        server.setHandler(handlers);
        server.setGracefulShutdown(1000);
        server.setStopAtShutdown(true);
        server.start();
	}

	@Override
	public void await() {
        try {
			server.join();
		} catch (InterruptedException e) {
			LOGGER.info("Interruped...");
		}
	}
	
	@Override
	public boolean isStarted() {
		return server != null && server.isStarted();
	}
	
	@Override
	public void stop() throws Exception {
		server.stop();
		server.destroy();
		server = null;
	}
}
