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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.musicmount.util.LoggingUtil;

public class MusicMountServerTomcat implements MusicMountServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountServerTomcat.class.getName());

	static {
		System.setProperty("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE", "true");
		LoggingUtil.configure("org.apache", Level.INFO);
	}
	
	final Filter AccessLogFilter = new Filter() {
		@Override
		public void init(FilterConfig filterConfig) throws ServletException {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
			final long requestTimestamp = System.currentTimeMillis();
			chain.doFilter(request, response); // serve request
			if (accessLog != null) {
				response.flushBuffer();

				final HttpServletRequest httpRequest = (HttpServletRequest) request;
				final HttpServletResponse httpResponse = (HttpServletResponse) response;			
				final long responseTimestamp = System.currentTimeMillis();
				accessLog.log(new AccessLog.Entry() {
					@Override
					public long getResponseTimestamp() {
						return responseTimestamp;
					}
					@Override
					public int getResponseStatus() {
						return httpResponse.getStatus();
					}
					@Override
					public String getResponseHeader(String header) {
						return httpResponse.getHeader(header);
					}
					@Override
					public String getRequestURI() {
						return httpRequest.getRequestURI();
					}
					@Override
					public long getRequestTimestamp() {
						return requestTimestamp;
					}
					@Override
					public String getRequestMethod() {
						return httpRequest.getMethod();
					}
				});
			}
		}

		@Override
		public void destroy() {
		}
	};

	static final Filter UTF8Filter = new Filter() {
		@Override
		public void init(FilterConfig filterConfig) throws ServletException {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
			response.setCharacterEncoding("UTF-8");
			chain.doFilter(request, response);
		}

		@Override
		public void destroy() {
		}
	};

	private static Context addContext(Tomcat tomcat, String contextPath, File baseDir) {
		Context mountContext = tomcat.addContext(contextPath, baseDir.getAbsolutePath());
		mountContext.addWelcomeFile("index.json");
		mountContext.addMimeMapping("json", "text/json");

		Wrapper defaultServlet = mountContext.createWrapper();
		defaultServlet.setName("default");
		defaultServlet.setServlet(new DefaultServlet());
		defaultServlet.addInitParameter("debug", "0");
		defaultServlet.addInitParameter("listings", "false");
		defaultServlet.setLoadOnStartup(1);
		mountContext.addChild(defaultServlet);
		mountContext.addServletMapping("/", "default");

		return mountContext;
	}

	private static void addBasicAuth(StandardContext context) {
		SecurityConstraint securityConstraint = new SecurityConstraint();
		securityConstraint.addAuthRole("user");
		SecurityCollection securityCollection = new SecurityCollection();
//		securityCollection.addMethod("GET"); // defaults to all methods
		securityCollection.addPattern("/*");
		securityConstraint.addCollection(securityCollection);

		LoginConfig loginConfig = new LoginConfig();
		loginConfig.setAuthMethod("BASIC");
		loginConfig.setRealmName("MusiMount");

		context.addConstraint(securityConstraint);
		context.setLoginConfig(loginConfig);
		context.addValve(new BasicAuthenticator());
	}

	private static boolean deleteRecursive(File parent) {
		if (parent.isDirectory()) {
			for (File child : parent.listFiles()) {
				deleteRecursive(child);
			}
		}
		return parent.delete();
	}

	private Tomcat tomcat;
	private Path workDir;
	private final AccessLog accessLog;
	
	public MusicMountServerTomcat(AccessLog accessLog) {
		this.accessLog = accessLog;
	}

	@Override
	public void start(FolderContext music, FolderContext mount, int port, final String user, final String password) throws Exception {
		tomcat = new Tomcat();
		workDir = Files.createTempDirectory("musicmount-");
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			Path workDir = MusicMountServerTomcat.this.workDir;
			@Override
			public void run() {
				if (Files.exists(workDir) && !deleteRecursive(workDir.toFile())) {
					LOGGER.warning("Could not delete temporary directory: " + workDir);
				}
			}
		}));
		tomcat.setBaseDir(workDir.toFile().getAbsolutePath());
		tomcat.setPort(port);
		tomcat.getConnector().setURIEncoding("UTF-8");
		tomcat.getConnector().setProperty("compression", "on");
		tomcat.getConnector().setProperty("compressionMinSize", "1024");
		tomcat.getConnector().setProperty("compressableMimeType", "text/json");
		tomcat.setSilent(true);

		// TODO: this fixes a resource-loading problem in WebappClassLoader when running through com.javafx.main.Main
		tomcat.getEngine().setParentClassLoader(Thread.currentThread().getContextClassLoader());

		Context mountContext = addContext(tomcat, mount.getPath(), mount.getFolder().getAbsoluteFile());
		mountContext.addWelcomeFile("index.json");
		mountContext.addMimeMapping("json", "text/json");

		Context musicContext = addContext(tomcat, music.getPath(), music.getFolder().getAbsoluteFile());
		musicContext.addMimeMapping("m4a", "audio/mp4");
		musicContext.addMimeMapping("mp3", "audio/mpeg");
		
		FilterDef utf8FilterDef = new FilterDef();
		utf8FilterDef.setFilterName("utf8-filter");
		utf8FilterDef.setFilter(UTF8Filter);
		FilterMap utf8FilterMap = new FilterMap();
		utf8FilterMap.setFilterName("utf8-filter");
		utf8FilterMap.addURLPattern("*");

		mountContext.addFilterDef(utf8FilterDef);
		mountContext.addFilterMap(utf8FilterMap);

		if (LOGGER.isLoggable(Level.FINE)) {
			FilterDef logFilterDef = new FilterDef();
			logFilterDef.setFilterName("log-filter");
			logFilterDef.setFilter(AccessLogFilter);
			FilterMap logFilterMap = new FilterMap();
			logFilterMap.setFilterName("log-filter");
			logFilterMap.addURLPattern("*");

			mountContext.addFilterDef(logFilterDef);
			mountContext.addFilterMap(logFilterMap);

			if (musicContext != null) {
				musicContext.addFilterDef(logFilterDef);
				musicContext.addFilterMap(logFilterMap);
			}
		}

		if (user != null && password != null) {
			tomcat.getEngine().setRealm(new RealmBase() {
				@Override
				protected Principal getPrincipal(String username) {
					String password = getPassword(username);
					return password != null ? new GenericPrincipal(username, password, Arrays.asList("user")) : null;
				}

				@Override
				protected String getPassword(String username) {
					return user.equals(username) ? password : null;
				}

				@Override
				protected String getName() {
					return "MusicMount";
				}
			});

			addBasicAuth((StandardContext) mountContext);
			if (musicContext != null) {
				addBasicAuth((StandardContext) musicContext);
			}
		}

		tomcat.start();
	}
	
	public void await() {
		tomcat.getServer().await();
	}
	
	public boolean isStarted() {
		return tomcat != null && tomcat.getServer().getState() == LifecycleState.STARTED;
	}
	
	public void stop() throws Exception {
		tomcat.stop();
		tomcat.destroy();
		tomcat = null;
		if (workDir != null && !deleteRecursive(workDir.toFile())) {
			LOGGER.warning("Could not delete temporary directory: " + workDir);
		}
		workDir = null;
	}
}
