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
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import org.musicmount.util.LoggingUtil;

import fi.iki.elonen.NanoHTTPD;

public class MusicMountNanoServer extends NanoHTTPD {
	static final Logger LOGGER = Logger.getLogger(MusicMountNanoServer.class.getName());
		
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
		LoggingUtil.configure(MusicMountNanoServer.class.getPackage().getName(), optionVerbose ? Level.FINER : Level.FINE);

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
		final MusicMountNanoServer server = new MusicMountNanoServer(optionPort, mountFolder, musicFolder, optionMusic, optionUser, optionPassword);

        try {
            server.start();
        } catch (IOException e) {
    		LOGGER.log(Level.SEVERE, "Couldn't start server", e);
            System.exit(-1);
        }

		LOGGER.info("Press CTRL-C to exit...");
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				if (server.isAlive()) {
					server.stop();
				}
			}
		}));

		Thread.sleep(Long.MAX_VALUE); // sleep forever
	}
	
	public static void main(String[] args) throws Exception {
		execute(MusicMountNanoServer.class.getSimpleName(), args);
	}

	/**
	 * Hack: nanohttpd uses <code>available()</code> to determine Content-Length!
	 * See https://github.com/NanoHttpd/nanohttpd/issues/51
	 */
	static class MusicMountResponseData extends FilterInputStream {
		int available;
		MusicMountResponseData(InputStream input, int available) {
			super(input);
			this.available = available;
		}
		@Override
		public synchronized int available() throws IOException {
			return available > 0 ? available : super.available();
		}
		@Override
		public synchronized int read() throws IOException {
			available = 0;
			return super.read();
		}
		@Override
		public synchronized int read(byte[] b, int off, int len) throws IOException {
			available = 0;
			return super.read(b, off, len);
		}
	}

	static class MusicMountResponse extends Response {
		final Long contentLength;
		
		MusicMountResponse(Response.Status status, String mimeType, String message) {
			super(status, mimeType, message);
			this.contentLength = null;
	        addHeader("Accept-Ranges", "bytes");
		}
		MusicMountResponse(Response.Status status, String mimeType, InputStream data, long contentLength) {
			super(status, mimeType, new MusicMountResponseData(data, (int)contentLength));
			this.contentLength = contentLength;
	        addHeader("Accept-Ranges", "bytes");
		}
	}
	
	private final File mountBase;
	private final File musicBase;
	private final String musicPath;
	private final String basicAuth;

	public MusicMountNanoServer(int port, File mountBase, File musicBase, String musicPath, String user, String password) {
		super(port);
		this.mountBase = mountBase;
		this.musicBase = musicBase;
		this.musicPath = musicPath.startsWith("/") ? musicPath : "/" + musicPath;
		this.basicAuth = user != null ? "Basic " + DatatypeConverter.printBase64Binary((user + ":" + password).getBytes()) : null;
	}
	
	private String mimeType(File file) {
        int dot = file.getName().lastIndexOf('.');
        String fileType =  dot > 0 ? file.getName().substring(dot + 1).toLowerCase() : null;
        if (fileType != null) {
        	switch (fileType) {
        	case "json" :
        		return "text/json";
        	case "m4a" :
        		return "audio/mp4";
        	case "mp3" :
        		return "audio/mpeg";
        	case "jpg" :
        	case "jpeg":
        		return "image/jpeg";
        	case "png" :
        		return "image/png";
        	}
        }
        return null;
	}
	
    public Response serve(IHTTPSession session) {
    	MusicMountResponse response = serve(session.getUri(), session.getHeaders());
		if (LOGGER.isLoggable(Level.FINER)) {
			int methodAndURIFormatLength = 50;
			StringBuilder builder = new StringBuilder();
			String uri = session.getUri();
	        int maxURILength = methodAndURIFormatLength - 1 - session.getMethod().name().length();
	        if (uri.length() > maxURILength) {
	        	uri = "..." +  uri.substring(uri.length() - maxURILength + 3);
	        }
	        String methodAndURI = String.format("%s %s", session.getMethod().name(), uri);
	        builder.append(String.format(String.format("%%-%ds", methodAndURIFormatLength), methodAndURI));
			builder.append(String.format("%4d", response.getStatus().getRequestStatus()));
	        if (response.contentLength != null) {
	        	builder.append(String.format("%,11dB", response.contentLength));
	        } else {
	            builder.append("            ");
	        }
	        LOGGER.finer(builder.toString());
		}
    	return response;
    }

    MusicMountResponse serve(String uri, Map<String, String> headers) {
        // strip down URI to relevant path 
        uri = uri.trim().replace(File.separatorChar, '/');
        if (uri.lastIndexOf('?') >= 0) {
            uri = uri.substring(0, uri.lastIndexOf('?'));
        }

        // apply basic authentication
		if (basicAuth != null && !basicAuth.equals(headers.get("authorization"))) {
	        MusicMountResponse response = new MusicMountResponse(Response.Status.UNAUTHORIZED, NanoHTTPD.MIME_PLAINTEXT, "Needs authentication.");
			response.addHeader("WWW-Authenticate", "Basic realm=\"MusicMount\"");
			return response;
		}
		
        File base = null;
        File file = null;
        if (uri.startsWith(musicPath)) {
        	base = musicBase;
        	file = new File(musicBase, uri.substring(musicPath.length()));
        } else {
        	base = mountBase;
        	file = new File(mountBase, uri);
        	if (file.isDirectory()) {
                if (!uri.endsWith("/")) {
                    uri += "/";
                }
                uri += "index.json";
        		file = new File(file, "index.json");
        	}
        }
        String mimeType = mimeType(file);

        if (mimeType == null || !file.getAbsolutePath().startsWith(base.getAbsolutePath())) {
            return new MusicMountResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Won't serve.");
        }
        if (!file.exists()) {
            return new MusicMountResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found.");
        }
        if (!file.canRead()) {
            return new MusicMountResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Cannot read file.");
        }
        if (file.isDirectory()) {
            return new MusicMountResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "No directory listing.");
        }

        try {
			return serveFile(file, headers, (mimeType + "; charset=utf-8"));
		} catch (IOException e) {
            return new MusicMountResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Could not serve file.");
		}
    }
	
    MusicMountResponse serveFile(File file, Map<String, String> headers, String contentType) throws IOException {
        // Support (simple) skipping:
        long startFrom = 0;
        long endAt = -1;
        String range = headers.get("range");
        if (range != null) {
            if (range.startsWith("bytes=")) {
                range = range.substring("bytes=".length());
                int minus = range.indexOf('-');
                try {
                    if (minus > 0) {
                        startFrom = Long.parseLong(range.substring(0, minus));
                        endAt = Long.parseLong(range.substring(minus + 1));
                    }
                } catch (NumberFormatException ignored) {
                	LOGGER.warning("Could not parse range: " + range);
                }
            }
        }

        // Calculate etag
        String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());

    	MusicMountResponse res;

        // Change return code and add Content-Range header when skipping is requested
        long fileLen = file.length();
        if (range != null && startFrom >= 0) {
            if (startFrom >= fileLen) {
                res = new MusicMountResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, null);
                res.addHeader("Content-Range", "bytes */" + fileLen);
                res.addHeader("ETag", etag);
            } else {
                if (endAt < 0) {
                    endAt = fileLen - 1;
                }
                long newLen = endAt - startFrom + 1;
                if (newLen < 0) {
                    newLen = 0;
                }

                FileInputStream fis = new FileInputStream(file);
                long position = 0;
                while (position < startFrom) {
                	long skipped = fis.skip(startFrom - position);
                	if (skipped <= 0) {
                		fis.close();
                		throw new IOException("Could not skip to start position");
                	}
                	position += skipped;
                }
                
                res = new MusicMountResponse(Response.Status.PARTIAL_CONTENT, contentType, fis, newLen);
                res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                res.addHeader("ETag", etag);
            }
        } else {
            if (etag.equals(headers.get("if-none-match"))) {
                res = new MusicMountResponse(Response.Status.NOT_MODIFIED, contentType, null);
                res.addHeader("ETag", etag);
            } else {
                FileInputStream fis = new FileInputStream(file);
                res = new MusicMountResponse(Response.Status.OK, contentType, fis, fileLen);
                res.addHeader("ETag", etag);
            }
        }

        return res;
    }
}
