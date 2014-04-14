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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.musicmount.util.LoggingUtil;

public class MusicMountJettyServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountJettyServer.class.getName());
	
	static class ConsoleRequestLog extends AbstractLifeCycle implements RequestLog {
		final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		final int methodAndURIFormatLength = 53; // -> line will be 80 chars wide
		
		@Override
		public void log(Request request, Response response) {
			StringBuilder builder = new StringBuilder();
			builder.append(dateFormat.format(request.getTimeStamp()));
			builder.append(" ");

//	        String credentials = request.getHeader(HttpHeaders.AUTHORIZATION);
//	        String user = null;
//            if (credentials != null) {                 
//                int space=credentials.indexOf(' ');
//                if (space > 0) {
//                    String method = credentials.substring(0,space);
//                    if ("basic".equalsIgnoreCase(method)) {
//                        credentials = credentials.substring(space+1);
//                        try {
//                            credentials = B64Code.decode(credentials, StringUtil.__ISO_8859_1);
//                        } catch (UnsupportedEncodingException e) {
//                        	  credentials = ";";
//                        }
//                        int i = credentials.indexOf(':');
//                        if (i > 0) {
//                            user = credentials.substring(0, i);
//                        }
//                    }
//                }
//            }
//			builder.append(user == null ? " n/a " : user);
//			builder.append(" ");

			String uri = request.getUri().toString();
	        int maxURILength = methodAndURIFormatLength - 1 - request.getMethod().length();
	        if (uri.length() > maxURILength) {
	        	uri = "..." +  uri.substring(uri.length() - maxURILength + 3);
	        }
	        String methodAndURI = String.format("%s %s", request.getMethod(), uri);
	        builder.append(String.format(String.format("%%-%ds", methodAndURIFormatLength), methodAndURI));
	        if (request.getAsyncContinuation().isInitial()) {
	    		builder.append(String.format("%4d", response.getStatus()));
	        } else {
	            builder.append("n/a ");
			}
	        long responseLength = response.getContentCount();
	        if (responseLength >= 0) {
	        	if (responseLength < 1024) {
	            	builder.append(String.format("%7dB", responseLength));
	        	} else if (responseLength < 1024 * 1024) {
	            	builder.append(String.format("%6.1fkB", responseLength / 1024.0));
	        	} else {
	            	builder.append(String.format("%6.1fMB", responseLength / 1024.0 / 1024.0));
	        	}
	        } else {
	            builder.append(" n/a  ");
	        }
	        long time = System.currentTimeMillis() - request.getTimeStamp();
	        if (time < 1000) {
	            builder.append(String.format("%4dms", time));
	        } else {
	            builder.append(String.format("%5.1fs", time / 1000.0));
	        }
			System.err.println(builder.toString());
		}
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
		System.err.println("       --user <user>     login user");
		System.err.println("       --password <pass> login password");
		System.err.println("       --verbose         more detailed console output");
		System.err.close();
		System.exit(1);	
	}
	
	public static void startServer(int port, File mountBase, File musicBase, String musicPath, String realm, String user, String password) throws Exception {
        ServletContextHandler mountContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        mountContext.setContextPath("/");
        mountContext.setSecurityHandler(user == null ? null : basicAuthentication(realm, user, password));
        mountContext.setResourceBase(mountBase.getAbsolutePath());
        mountContext.setWelcomeFiles(new String[] { "index.json" });
        mountContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "true");
        MimeTypes mountTypes = new MimeTypes();
        mountTypes.addMimeMapping("json", MimeTypes.TEXT_JSON_UTF_8);
        mountContext.setMimeTypes(mountTypes);
        mountContext.addServlet(new ServletHolder(new DefaultServlet()), "/*");

        ServletContextHandler musicContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        musicContext.setContextPath(musicPath.startsWith("/") ? musicPath : "/" + musicPath);
        musicContext.setSecurityHandler(user == null ? null : basicAuthentication(realm, user, password));
        musicContext.setResourceBase(musicBase.getAbsolutePath());
        mountContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
        MimeTypes musicTypes = new MimeTypes();
        musicTypes.addMimeMapping("m4a", "audio/mp4");
        musicTypes.addMimeMapping("mp3", "audio/mpeg");
        musicContext.setMimeTypes(musicTypes);
        musicContext.addServlet(new ServletHolder(new DefaultServlet()), "/*");

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[] { mountContext, musicContext });
        
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(new ConsoleRequestLog());   

        HandlerCollection handlers = new HandlerCollection();
        handlers.setHandlers(new Handler[]{contexts, new DefaultHandler(), requestLogHandler});
        
        Server server = new Server(port);
        server.setHandler(handlers);
        server.start();
        server.join();
	}

    private static final SecurityHandler basicAuthentication(String realm, String username, String password) {
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
		String optionUser = null;
		String optionPassword = null;
		boolean optionVerbose = false;

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
			case "--user":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				optionUser = args[i];
				break;
			case "--password":
				if (++i == optionsLength) {
					exitWithError(command, "invalid arguments");
				}
				optionPassword = args[i];
				break;
			case "--verbose":
				optionVerbose = true;
				break;
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
			exitWithError(command, String.format("music folder doesn't exist: %s", musicFolder));
		}
		File mountFolder = new File(args[optionsLength + 1]);
		if (!mountFolder.exists() || mountFolder.isFile()) {
			exitWithError(command, String.format("output folder doesn't exist: %s", mountFolder));
		}
		if ((optionUser == null) != (optionPassword == null)) {
			exitWithError(command, String.format("either both or none of user/password must be given: %s/%s", optionUser, optionPassword));
		}

		/**
		 * Configure logging
		 */
		LoggingUtil.configure(MusicMountJettyServer.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);
		LoggingUtil.configure("org.eclipse.jetty", Level.INFO);
		Log.setLog(new JavaUtilLog("org.eclipse.jetty")); // use java.util.logging

		String hostname = InetAddress.getLoopbackAddress().getHostName();
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			LOGGER.log(Level.WARNING, "Could not determine local host name, showing loopback name", e);
		}

		LOGGER.info(String.format("Mount Settings"));
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Site: http://%s:%d", hostname, optionPort));
		if (optionUser != null) {
			LOGGER.info(String.format("User: %s", optionUser));
			LOGGER.info(String.format("Pass: %s", optionPassword));
		}
		LOGGER.info(String.format("--------------"));
		LOGGER.info(String.format("Starting Server..."));
		startServer(optionPort, mountFolder, musicFolder, optionMusic, "MusicMount", optionUser, optionPassword);
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountJettyServer.class.getSimpleName(), args);
	}
}
