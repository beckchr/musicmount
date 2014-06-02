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
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Experimental!
 * 
 * not really working... 
 */
public class MusicMountServerNano implements MusicMountServer {
	static final Logger LOGGER = Logger.getLogger(MusicMountServerNano.class.getName());
	
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
	
	private static String mimeType(File file) {
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

	private FolderContext mountContext;
	private FolderContext musicContext;
	private String basicAuth;
	private NanoHTTPD nano;
	private final AccessLog accessLog;
	
	public MusicMountServerNano(AccessLog accessLog) {
		this.accessLog = accessLog;
//      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
//			@Override
//			public void run() {
//				if (isAlive()) {
//					stop();
//				}
//			}
//		}));
	}
	
	@Override
	public void start(FolderContext music, FolderContext mount, int port, String user, String password) throws Exception {
		this.mountContext = mount;
		this.musicContext = music;
		this.basicAuth = user != null ? "Basic " + DatatypeConverter.printBase64Binary((user + ":" + password).getBytes()) : null;
		this.nano = new NanoHTTPD(port) {
			@Override
		    public Response serve(final IHTTPSession session) {
				final long requestTimestamp = System.currentTimeMillis();
		    	final MusicMountResponse response = MusicMountServerNano.this.serve(session.getUri(), session.getHeaders());
				if (accessLog != null) {
					final long responseTimestamp = System.currentTimeMillis();
					accessLog.log(new AccessLog.Entry() {
						@Override
						public long getResponseTimestamp() {
							return responseTimestamp;
						}
						@Override
						public int getResponseStatus() {
							return response.getStatus() != null ? response.getStatus().getRequestStatus() : 0;
						}
						@Override
						public String getResponseHeader(String header) {
							if (header.equalsIgnoreCase("Content-Length")) {
								if (response.contentLength != null) {
									return String.valueOf(response.contentLength);
								}
							}
							return null;
						}
						@Override
						public String getRequestURI() {
							return session.getUri();
						}
						@Override
						public long getRequestTimestamp() {
							return requestTimestamp;
						}
						@Override
						public String getRequestMethod() {
							return session.getMethod().name();
						}
					});
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
		};
		nano.start();
	}
	
	@Override
	public void start(FolderContext music, MountContext mount, int port, String user, String password) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean isStarted() {
		return nano != null && nano.isAlive();
	}
	
	@Override
	public void await() {
		try {
			Thread.sleep(Long.MAX_VALUE);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // sleep forever
	}
	
	@Override
	public void stop() {
		nano.stop();
		nano = null;
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
		
        FolderContext context = null;
        if (uri.startsWith(musicContext.getPath())) {
        	context = musicContext;
        } else if (uri.startsWith(mountContext.getPath())) {
        	context = mountContext;
        } else {
            return new MusicMountResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found.");
        }

    	File file = new File(context.getFolder(), uri.substring(context.getPath().length()));
    	if (context == mountContext && file.isDirectory()) {
            if (!uri.endsWith("/")) {
                uri += "/";
            }
            uri += "index.json";
    		file = new File(file, "index.json");
    	}
    	
        String mimeType = mimeType(file);

        if (mimeType == null || !file.getAbsolutePath().startsWith(context.getFolder().getAbsolutePath())) {
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
