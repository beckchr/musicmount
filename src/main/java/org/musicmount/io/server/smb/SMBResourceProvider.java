package org.musicmount.io.server.smb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import org.musicmount.io.server.ServerFileAttributes;
import org.musicmount.io.server.ServerFileSystem;
import org.musicmount.io.server.ServerPath;
import org.musicmount.io.server.ServerResourceProvider;

public class SMBResourceProvider extends ServerResourceProvider {
	private final NtlmPasswordAuthentication auth;
	private final String host;

	public SMBResourceProvider(URI serverUri) {
		this(new ServerFileSystem(serverUri));
	}
	
	public SMBResourceProvider(URI serverUri, String userInfo) {
		this(new ServerFileSystem(serverUri, userInfo));
	}

	public SMBResourceProvider(String host, String path, String user, String password) throws URISyntaxException {
		this(new ServerFileSystem("smb", host, -1, path, user, password));
	}

	public SMBResourceProvider(String authority, String path) throws URISyntaxException {
		this(new ServerFileSystem("smb", authority, path));
	}

	private SMBResourceProvider(ServerFileSystem fileSystem) {
		super(fileSystem);
		if (!"smb".equals(fileSystem.getScheme())) {
			throw new IllegalArgumentException("Scheme must be \"smb\"");
		}
		host = fileSystem.getHost();
		auth = new NtlmPasswordAuthentication(fileSystem.getUserInfo());
//		SmbSession.logon(UniAddress.getByName(host), auth);
	}
	
	SmbFile getFile(ServerPath path) throws IOException {
		return new SmbFile(String.format("smb://%s/%s", host, path.toAbsolutePath().normalize()), auth);
	}

	@Override
	protected BasicFileAttributes getFileAttributes(ServerPath path) throws IOException {
		return new SMBFileAttributes(getFile(path));
	}

	@Override
	protected List<ServerFileAttributes> getChildrenAttributes(ServerPath folder) throws IOException {
		List<ServerFileAttributes> attributes = new ArrayList<>();
		for (SmbFile file : getFile(folder).listFiles()) {
			if (file.isDirectory()) {
				attributes.add(new SMBFileAttributes(file));
				continue;
			}
			if (file.isFile()) {
				attributes.add(new SMBFileAttributes(file));
			}
		}
		return attributes;
	}

	@Override
	protected boolean exists(ServerPath path) throws IOException {
		return getFile(path).exists();
	}

	@Override
	protected void delete(ServerPath path) throws IOException {
		getFile(path).delete();
	}

	@Override
	protected void createDirectory(ServerPath path) throws IOException {
		getFile(path).mkdir();
	}

	@Override
	protected InputStream getInputStream(ServerPath path) throws IOException {
		return new SmbFileInputStream(getFile(path));
	}

	@Override
	protected OutputStream getOutputStream(ServerPath path) throws IOException {
		return new SmbFileOutputStream(getFile(path));
	}
}
