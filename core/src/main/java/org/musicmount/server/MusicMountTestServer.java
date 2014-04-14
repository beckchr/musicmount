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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
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
import org.apache.catalina.startup.DigesterFactory;
import org.apache.catalina.startup.Tomcat;
import org.musicmount.io.file.FileResource;
import org.musicmount.io.file.FileResourceProvider;
import org.musicmount.util.LoggingUtil;

public class MusicMountTestServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountTestServer.class.getName());

	static {
		LoggingUtil.configure("org.apache", Level.INFO);
		LoggingUtil.configure(DigesterFactory.class.getName(), Level.SEVERE); // get rid of warnings on missing jsp schema files		
	}
	
	static final Filter AccessLogFilter = new Filter() {
		@Override
		public void init(FilterConfig filterConfig) throws ServletException {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
			long timestamp = System.currentTimeMillis();
			chain.doFilter(request, response);
			if (LOGGER.isLoggable(Level.FINER)) {
				response.flushBuffer();

				HttpServletRequest httpRequest = (HttpServletRequest) request;
				HttpServletResponse httpResponse = (HttpServletResponse) response;

				StringBuilder builder = new StringBuilder();
				String uri = httpRequest.getRequestURI();
				final int methodAndURIFormatLength = 42;
				int maxURILength = methodAndURIFormatLength - 1 - httpRequest.getMethod().length();
				if (uri.length() > maxURILength) {
					uri = "..." + uri.substring(uri.length() - maxURILength + 3);
				}
				String methodAndURI = String.format("%s %s", httpRequest.getMethod(), uri);
				builder.append(String.format(String.format("%%-%ds", methodAndURIFormatLength), methodAndURI));
				builder.append(String.format("%4d", httpResponse.getStatus()));
				String contentLengthHeader = httpResponse.getHeader("Content-Length");
				if (contentLengthHeader != null) {
					builder.append(String.format("%,11dB", Long.valueOf(contentLengthHeader)));
				} else {
					builder.append("            ");
				}
				long time = System.currentTimeMillis() - timestamp;
				builder.append(String.format("%5.2fs", time / 1000.0));
				LOGGER.finer(builder.toString());
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

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder> <mount_folder>", command));
		System.err.println();
		System.err.println("Launch MusicMount site in <mount_folder> with music from <music_folder>");
		System.err.println();
		System.err.println("         <music_folder>   input folder (containing the music library)");
		System.err.println("         <mount_folder>   output folder (to contain the generated site)");
		System.err.println();
		System.err.println("Folders must be local.");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>     music path, default is relative path from <mountFolder> to <musicFolder>");
		System.err.println("       --port <port>      launch HTTP server on specified port (default 8080)");
		System.err.println("       --user <user>      login user");
		System.err.println("       --password <pass>  login password");
		System.err.println("       --verbose          more detailed console output");
		System.err.close();
		System.exit(1);
	}

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

	Tomcat tomcat;

	public void start(FileResource musicFolder, FileResource mountFolder, String musicPath, int port, final String user, final String password) throws Exception {
		LOGGER.info("Music folder: " + musicFolder.getPath());
		LOGGER.info("Mount folder: " + mountFolder.getPath());
		LOGGER.info("Music path  : " + musicPath);

		tomcat = new Tomcat();
		final File workDir = File.createTempFile("musicmount-", null);
		workDir.delete();
		workDir.mkdir();
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				if (!deleteRecursive(workDir)) {
					LOGGER.fine("Could not delete temporary directory: " + workDir);
				}
			}
		}));
		tomcat.setBaseDir(workDir.getAbsolutePath());
		tomcat.setPort(port);
		tomcat.getConnector().setURIEncoding("UTF-8");
		tomcat.setSilent(true);

		Context mountContext = addContext(tomcat, "/musicmount", mountFolder.getPath().toFile());
		mountContext.addWelcomeFile("index.json");
		mountContext.addMimeMapping("json", "text/json");

		Context musicContext = null;
		if (musicPath == null || musicPath.isEmpty()) {
			if (!mountFolder.equals(musicFolder)) {
				throw new IllegalArgumentException("Missing music path");
			}
		} else { // calculate music context path and add context
			if (!musicPath.startsWith("/")) {
				if (musicPath.startsWith("../")) { // ../music --> /music
					musicPath = musicPath.substring(2);
				} else { // music or ./music --> /musicmount/music
					if (musicPath.startsWith("./")) {
						musicPath = musicPath.substring(2);
					}
					musicPath = "/musicmount/" + musicPath;
				}
			}
			if (musicPath.indexOf("..") >= 0) {
				throw new IllegalArgumentException("Unsupported music path");
			}
			musicContext = addContext(tomcat, musicPath, musicFolder.getPath().toFile());
			musicContext.addMimeMapping("m4a", "audio/mp4");
			musicContext.addMimeMapping("mp3", "audio/mpeg");
		}
		
		FilterDef utf8FilterDef = new FilterDef();
		utf8FilterDef.setFilterName("utf8-filter");
		utf8FilterDef.setFilter(UTF8Filter);
		FilterMap utf8FilterMap = new FilterMap();
		utf8FilterMap.setFilterName("utf8-filter");
		utf8FilterMap.addURLPattern("*");

		mountContext.addFilterDef(utf8FilterDef);
		mountContext.addFilterMap(utf8FilterMap);

		if (LOGGER.isLoggable(Level.FINER)) {
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
		tomcat.getServer().await();
	}
	
	public void stop() throws Exception {
		tomcat.stop();
		tomcat.destroy();
		tomcat = null;
	}

	/**
	 * Launch HTTP Server
	 * @param command command name (e.g. "test")
	 * @param args musicFolder, mountFolder
	 * @throws Exception something went wrong...
	 */
	public static void execute(String command, String[] args) throws Exception {
		String optionMusic = null;
		int optionPort = 8080;
		String optionUser = null;
		String optionPassword = null;
		boolean optionVerbose = false;

		int optionsLength = 0;
		boolean optionsDone = false;
		while (optionsLength < args.length && !optionsDone) {
			switch (args[optionsLength]) {
			case "--music":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionMusic = args[optionsLength];
				break;
			case "--port":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionPort = Integer.parseInt(args[optionsLength]);
				break;
			case "--user":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionUser = args[optionsLength];
				break;
			case "--password":
				if (++optionsLength == args.length) {
					exitWithError(command, "invalid arguments");
				}
				optionPassword = args[optionsLength];
				break;
			case "--verbose":
				optionVerbose = true;
				break;
			default:
				if (args[optionsLength].startsWith("-")) {
					exitWithError(command, "unknown option: " + args[optionsLength]);
				} else {
					optionsDone = true;
				}
			}
			if (!optionsDone) {
				optionsLength++;
			}
		}
		for (int i = optionsLength; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				exitWithError(command, "invalid arguments");
			}
		}

		FileResource musicFolder = null;
		FileResource mountFolder = null;
		switch (args.length - optionsLength) {
		case 0:
		case 1:
			exitWithError(command, "missing arguments");
			break;
		case 2:
			musicFolder = new FileResourceProvider(args[optionsLength]).getBaseDirectory();
			mountFolder = new FileResourceProvider(args[optionsLength + 1]).getBaseDirectory();
			break;
		default:
			exitWithError(command, "bad arguments");
		}
		if (mountFolder.exists()) {
			if (!mountFolder.isDirectory()) {
				exitWithError(command, "mount folder is not a directory: " + mountFolder);
			}
		} else {
			exitWithError(command, "mount folder doesn't exist: " + musicFolder);
		}
		if (musicFolder.exists()) {
			if (!musicFolder.isDirectory()) {
				exitWithError(command, "music folder is not a directory: " + musicFolder);
			}
		} else {
			exitWithError(command, "music folder doesn't exist: " + musicFolder);
		}
		if ((optionUser == null) != (optionPassword == null)) {
			exitWithError(command, String.format("either both or none of user/password must be given: %s/%s", optionUser, optionPassword));
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountTestServer.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);
		
		if (optionMusic == null && musicFolder.getPath().getRoot().equals(mountFolder.getPath().getRoot())) {
			try { // calculate relative path from mountFolder to musicFolder
				optionMusic = mountFolder.getPath().relativize(musicFolder.getPath()).toString();
			} catch (IllegalArgumentException e) {
				// cannot relativize
			}
		}
		if (optionMusic == null) {
			exitWithError(command, "could not calculate music path as relative path from <mountFolder> to <musicFolder>, use --music <path>");
		}
		optionMusic = optionMusic.replace(FileSystems.getDefault().getSeparator(), "/");

		LOGGER.info("Music folder: " + musicFolder.getPath());
		LOGGER.info("Mount folder: " + mountFolder.getPath());
		LOGGER.info("Music path  : " + optionMusic);
		LOGGER.info("");

		String hostname = InetAddress.getLoopbackAddress().getHostName();
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			LOGGER.log(Level.WARNING, "Could not determine local host name, showing loopback name", e);
		}

		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: http://%s:%d/musicmount/", hostname, optionPort));
		if (optionUser != null) {
			LOGGER.info(String.format("User: %s", optionUser));
			LOGGER.info(String.format("Pass: %s", optionPassword));
		}
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Starting Server..."));
		LOGGER.info("Press CTRL-C to exit...");
		new MusicMountTestServer().start(musicFolder, mountFolder, optionMusic, optionPort, optionUser, optionPassword);
	}

	public static void main(String[] args) throws Exception {
		execute(MusicMountTestServer.class.getSimpleName(), args);
	}
}
