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
package org.musicmount.io.server.dav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.util.EntityUtils;
import org.musicmount.io.server.ServerFileAttributes;
import org.musicmount.io.server.ServerFileSystem;
import org.musicmount.io.server.ServerPath;
import org.musicmount.io.server.ServerResourceProvider;
import org.musicmount.util.PositionInputStream;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.impl.SardineImpl;
import com.github.sardine.impl.handler.VoidResponseHandler;
import com.github.sardine.impl.io.ContentLengthInputStream;

public class DAVResourceProvider extends ServerResourceProvider {
	/*
	 * Sardine is NOT thread-safe (because of HTTPContext)
	 */
	private final ThreadLocal<Sardine> sardine = new ThreadLocal<Sardine>() {
		protected Sardine initialValue() {
			return createSardine(fileSystem);
		}
	};
	private final ServerFileSystem fileSystem;

	public DAVResourceProvider(URI serverUri) {
		this(new ServerFileSystem(serverUri));
	}

	public DAVResourceProvider(URI serverUri, String userInfo) {
		this(new ServerFileSystem(serverUri, userInfo));
	}

	public DAVResourceProvider(String scheme, String authority, String path) throws URISyntaxException {
		this(new ServerFileSystem(scheme, authority, path));
	}
	
	public DAVResourceProvider(String scheme, String host, int port, String path, String user, String password) throws URISyntaxException {
		this(new ServerFileSystem(scheme, host, port, path, user, password));
	}
	
	protected DAVResourceProvider(ServerFileSystem fileSystem) {
		super(fileSystem);
		if (!"http".equals(fileSystem.getScheme()) && !"https".equals(fileSystem.getScheme())) {
			throw new IllegalArgumentException("Scheme must be \"http\" or \"https\"");
		}
		this.fileSystem = fileSystem;
	}

	protected Sardine createSardine(final ServerFileSystem fileSystem) {
		/*
		 * extract user/password
		 */
		String user = null;
		String password = null;
		if (fileSystem.getUserInfo() != null) {
			String[] userAndPassword = fileSystem.getUserInfo().split(":");
			user = userAndPassword[0];
			password = userAndPassword.length > 1 ? userAndPassword[1] : null;
		}

		/*
		 * create customized sardine
		 */
		return new SardineImpl(user, password, null) {
			@Override
			protected Registry<ConnectionSocketFactory> createDefaultSchemeRegistry() {
				ConnectionSocketFactory socketFactory;
				if ("https".equalsIgnoreCase(fileSystem.getScheme())) {
					socketFactory = createDefaultSecureSocketFactory();
				} else {
					socketFactory = createDefaultSocketFactory();
				}
				return RegistryBuilder.<ConnectionSocketFactory>create()
						.register(fileSystem.getScheme(), socketFactory)
						.build();
			}
			@Override
			protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
				try { // trust anybody...
					SSLContext context = SSLContext.getInstance("TLS");
					X509TrustManager trustManager = new X509TrustManager() {
						public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {}
						public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {}
						public X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[0];
						}
					};
					context.init(null, new TrustManager[]{ trustManager }, null);
					return new SSLConnectionSocketFactory(context, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
				} catch (NoSuchAlgorithmException | KeyManagementException e) {
					// should not happen...
				}
				return super.createDefaultSecureSocketFactory();
			}
			@Override
			protected <T> T execute(HttpRequestBase request, ResponseHandler<T> responseHandler) throws IOException {
				/*
				 * Sardine re-executes a PUT request after a org.apache.http.NoHttpResponseException without resetting it...
				 */
				if (request.isAborted()) {
					request.reset();
				}
				return super.execute(request, responseHandler);
			}
			@Override
			public ContentLengthInputStream get(String url, Map<String, String> headers) throws IOException {
				/*
				 * abort rather than consume entity for better performance
				 */
				final HttpGet get = new HttpGet(url);
				for (String header : headers.keySet()) {
					get.addHeader(header, headers.get(header));
				}
				// Must use #execute without handler, otherwise the entity is consumed already after the handler exits.
				final HttpResponse response = this.execute(get);
				VoidResponseHandler handler = new VoidResponseHandler();
				try {
					handler.handleResponse(response);
					// Will consume or abort the entity when the stream is closed.
					PositionInputStream positionInputStream = new PositionInputStream(response.getEntity().getContent()) {
						public void close() throws IOException {
							if (getPosition() == response.getEntity().getContentLength()) {
								EntityUtils.consume(response.getEntity());
							} else { // partial read or unknown content length
								get.abort();
							}
						}
					};
					return new ContentLengthInputStream(positionInputStream, response.getEntity().getContentLength());
				} catch (IOException ex) {
					get.abort();
					throw ex;
				}
			}
		};
	}

	protected Sardine getSardine() {
		return sardine.get();
	}

	@Override
	protected BasicFileAttributes getFileAttributes(ServerPath path) throws IOException {
		List<DavResource> list = getSardine().list(path.toUri().toString(), 0);
		if (list.size() != 1) {
			throw new IOException("Could not get file attributes for path: " + path);
		}
		return new DAVFileAttributes(list.get(0));
	}

	@Override
	protected List<ServerFileAttributes> getChildrenAttributes(ServerPath folder) throws IOException {
		List<DavResource> resources = getSardine().list(folder.toUri().toString(), 1);
		
		/*
		 * Older lighttpd servers seems NOT to include the parent as first element!
		 * We therefore check if the first resource matches the parent folder.
		 */
		if (resources.isEmpty()) {
			return Collections.emptyList();
		}
		int start = 1; // collection includes parent folder
		if (resources.get(0).isDirectory()) {
			if (fileSystem.getPath(resources.get(0).getPath()).toRealPath().getNameCount() == folder.toRealPath().getNameCount() + 1) {
				start = 0;
			}
		} else {
			start = 0;
		}
		List<ServerFileAttributes> attributes = new ArrayList<>(resources.size() - start);
		for (int i = start; i < resources.size(); i++) {
			attributes.add(new DAVFileAttributes(resources.get(i)));
		}
		return attributes;
	}

	@Override
	protected boolean exists(ServerPath path) throws IOException {
		if (path.isDirectory()) {
			try {
				return getFileAttributes(path).isDirectory();
			} catch (HttpResponseException e) {
				if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
					return false;
				} else {
					throw new IOException(e.getMessage() + " (" + e.getStatusCode() + ")", e);
				}
			}
		} else { // HEAD, doesn't work for directories
			return getSardine().exists(path.toUri().toString());
		}
	}

	@Override
	protected void delete(ServerPath path) throws IOException {
		getSardine().delete(path.toUri().toString());
	}

	@Override
	protected void createDirectory(ServerPath path) throws IOException {
		getSardine().createDirectory(path.toUri().toString());
	}

	@Override
	protected InputStream getInputStream(ServerPath path) throws IOException {
		return getSardine().get(path.toUri().toString());
	}

	@Override
	protected OutputStream getOutputStream(final ServerPath path) throws IOException {
		return new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				getSardine().put(path.toUri().toString(), toByteArray());
			}
		};
	}
}
