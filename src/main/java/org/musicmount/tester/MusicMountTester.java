/*
 * Copyright 2013 Odysseus Software GmbH
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
package org.musicmount.tester;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.DigesterFactory;
import org.apache.catalina.startup.Tomcat;
import org.musicmount.util.LoggingUtil;

public class MusicMountTester {
	static final Logger LOGGER = Logger.getLogger(MusicMountTester.class.getName());

	static final Filter AccessLogFilter = new Filter() {
		final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		final int methodAndURIFormatLength = 53; // -> line will be 80 chars wide

		@Override
		public void init(FilterConfig filterConfig) throws ServletException {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
			HttpServletRequest httpRequest = (HttpServletRequest)request;
			HttpServletResponse httpResponse = (HttpServletResponse)response;

			long timestamp = System.currentTimeMillis();
			
			response.setCharacterEncoding("UTF-8");
			chain.doFilter(request, response);
			response.flushBuffer();
			
			StringBuilder builder = new StringBuilder();
			builder.append(dateFormat.format(new Date(timestamp)));
			builder.append(" ");
			String uri = httpRequest.getRequestURI();
	        int maxURILength = methodAndURIFormatLength - 1 - httpRequest.getMethod().length();
	        if (uri.length() > maxURILength) {
	        	uri = "..." +  uri.substring(uri.length() - maxURILength + 3);
	        }
	        String methodAndURI = String.format("%s %s", httpRequest.getMethod(), uri);
	        builder.append(String.format(String.format("%%-%ds", methodAndURIFormatLength), methodAndURI));
    		builder.append(String.format("%4d", httpResponse.getStatus()));
	        String responseLengthString = httpResponse.getHeader("Content-Length");
	        if (responseLengthString != null) {
	        	long responseLength = Long.valueOf(responseLengthString);
	        	if (responseLength < 1024) {
	            	builder.append(String.format("%7dB", responseLength));
	        	} else if (responseLength < 1024 * 1024) {
	            	builder.append(String.format("%6.1fkB", responseLength / 1024.0));
	        	} else {
	            	builder.append(String.format("%6.1fMB", responseLength / 1024.0 / 1024.0));
	        	}
	        } else {
	            builder.append("   n/a  ");
	        }
	        long time = System.currentTimeMillis() - timestamp;
	        if (time < 1000) {
	            builder.append(String.format("%4dms", time));
	        } else {
	            builder.append(String.format("%5.1fs", time / 1000.0));
	        }
			System.err.println(builder.toString());
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
		
	/**
	 * Configure logging
	 */
	static {
		LoggingUtil.configure(MusicMountTester.class.getPackage().getName(), Level.FINE);
		LoggingUtil.configure("org.apache", Level.INFO);
		LoggingUtil.configure(DigesterFactory.class.getName(), Level.SEVERE); // get rid of warnings on missing jsp schema files		
	}

	static void exitWithError(String command, String error) {
		System.err.println();
		System.err.println("*** " + (error == null ? "internal error" : error));
		System.err.println();
		System.err.println(String.format("Usage: %s [options] <music_folder> <mount_folder>", command));
		System.err.println();
		System.err.println("Launch MusicMount site in <mount_folder> with music from <music_folder>");
		System.err.println();
		System.err.println("Options:");
		System.err.println("       --music <path>    music path prefix, default is 'music'");
		System.err.println("       --port <port>     launch HTTP server on specified port (default 8080)");
//		System.err.println("       --name <name>     service name/realm (default 'Test')");
//		System.err.println("       --user <user>     login user id (default 'test')");
//		System.err.println("       --password <pass> login password (default 'testXXX', XXX random number)");
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
	
	public static void startServer(int port, File mountBase, File musicBase, String musicPath) throws Exception {
		Tomcat tomcat = new Tomcat();
		File workDir = File.createTempFile("musicmount-test", "");
		workDir.delete();
		workDir.mkdir();
        tomcat.setBaseDir(workDir.getAbsolutePath());
        tomcat.setPort(port);
        tomcat.getConnector().setURIEncoding("UTF-8");
        
        Context mountContext = addContext(tomcat, "/", mountBase);
        mountContext.addWelcomeFile("index.json");
        mountContext.addMimeMapping("json", "text/json");

        FilterDef logFilterDef = new FilterDef();
        logFilterDef.setFilterName("log-filter");
        logFilterDef.setFilter(AccessLogFilter);
        FilterMap logFilterMap = new FilterMap(); 
        logFilterMap.setFilterName("log-filter"); 
        logFilterMap.addURLPattern("*"); 

        mountContext.addFilterDef(logFilterDef);
        mountContext.addFilterMap(logFilterMap);
        
        FilterDef utf8FilterDef = new FilterDef();
        utf8FilterDef.setFilterName("utf8-filter");
        utf8FilterDef.setFilter(UTF8Filter);
        FilterMap utf8FilterMap = new FilterMap(); 
        utf8FilterMap.setFilterName("utf8-filter"); 
        utf8FilterMap.addURLPattern("*"); 

        mountContext.addFilterDef(utf8FilterDef);
        mountContext.addFilterMap(utf8FilterMap); 

        Context musicContext = addContext(tomcat, musicPath.startsWith("/") ? musicPath : "/" + musicPath, musicBase);
        musicContext.addMimeMapping("m4a", "audio/mp4");
        musicContext.addMimeMapping("mp3", "audio/mpeg");

        musicContext.addFilterDef(logFilterDef);
        musicContext.addFilterMap(logFilterMap);

        tomcat.start();
        tomcat.getServer().await(); 
	}
    
	/**
	 * Launch HTTP Server
	 * @param args inputFolder, outputFolder
	 * @throws Exception
	 */
	public static void execute(String command, String[] args) throws Exception {
		if (args.length < 2) {
			exitWithError(command, "missing arguments");
		}
		String optionMusic = "music";
		int optionPort = 8080;
//		String optionName = "Test";
//		String optionUser = "test";
//		String optionPassword = String.format("test%03d", new Random().nextInt(1000));

		int optionsLength = args.length - 2;
		for (int i = 0; i < optionsLength; i++) {
			switch (args[i]) {
			case "--music":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				optionMusic = args[i];
				break;
			case "--port":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				optionPort = Integer.parseInt(args[i]);
				break;
//			case "--name":
//				if (++i == optionsLength) {
//					exitWithError(command, "invalid arguments");
//				}
//				optionName = args[i];
//				break;
//			case "--user":
//				if (++i == optionsLength) {
//					exitWithError(command, "invalid arguments");
//				}
//				optionUser = args[i];
//				break;
//			case "--password":
//				if (++i == optionsLength) {
//					exitWithError(command, "invalid arguments");
//				}
//				optionPassword = args[i];
//				break;
			default:
				if (args[i].startsWith("-")) {
					exitWithError(command, "unknown option: " + args[i]);
				} else {
					exitWithError(command, "invalid arguments");
				}
			}
		}
		for (int i = optionsLength; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				exitWithError(command, "invalid arguments");
			}
		}

		File musicFolder = new File(args[optionsLength]);
		if (!musicFolder.exists() || musicFolder.isFile()) {
			exitWithError(command, "music folder doesn't exist: " + musicFolder);
		}
		File mountFolder = new File(args[optionsLength + 1]);
		if (!mountFolder.exists() || mountFolder.isFile()) {
			exitWithError(command, "output folder doesn't exist" + mountFolder);
		}		
		String hostname = InetAddress.getLoopbackAddress().getHostName();
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			LOGGER.log(Level.WARNING, "Could not determine local host name, showing loopback name", e);
		}

		LOGGER.info(String.format("Starting Server..."));
		LOGGER.info(String.format("URL:  http://%s:%d", hostname, optionPort));
//		LOGGER.info(String.format("Name: %s", optionName));
//		LOGGER.info(String.format("User: %s", optionUser));
//		LOGGER.info(String.format("Pass: %s", optionPassword));
		LOGGER.info("Press CTRL-C to exit...");
		startServer(optionPort, mountFolder, musicFolder, optionMusic);
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountTester.class.getSimpleName(), args);
	}
}
