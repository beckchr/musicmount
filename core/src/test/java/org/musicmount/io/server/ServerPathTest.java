package org.musicmount.io.server;

import java.io.IOException;
import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

public class ServerPathTest {
	ServerFileSystem fileSystem = new ServerFileSystem(URI.create("foo://bar/foobar/"));

	@Test
	public void testServerPath_() {
		Assert.assertEquals("", new ServerPath(fileSystem, "").toString());
		Assert.assertEquals("", new ServerPath(fileSystem, "", "").toString());

		Assert.assertEquals("/", new ServerPath(fileSystem, "/").toString());
		Assert.assertEquals("a", new ServerPath(fileSystem, "a").toString());
		Assert.assertEquals("a/b", new ServerPath(fileSystem, "a/b").toString());
		Assert.assertEquals("a/b", new ServerPath(fileSystem, "a", "b").toString());
		Assert.assertEquals("a/b/", new ServerPath(fileSystem, "a", "b", "/").toString());
		Assert.assertEquals("a/b/", new ServerPath(fileSystem, "a", "b/").toString());
		Assert.assertEquals("a/b/", new ServerPath(fileSystem, "a", "b//").toString());

		Assert.assertEquals("/a", new ServerPath(fileSystem, "/a").toString());
		Assert.assertEquals("/a/b", new ServerPath(fileSystem, "/a/b").toString());
		Assert.assertEquals("/a/b", new ServerPath(fileSystem, "/a", "b").toString());
		Assert.assertEquals("/a/b/", new ServerPath(fileSystem, "/a", "b", "/").toString());
		Assert.assertEquals("/a/b/", new ServerPath(fileSystem, "/a", "b/").toString());
		Assert.assertEquals("/a/b/", new ServerPath(fileSystem, "/a", "b//").toString());
		Assert.assertEquals("/a/b/", new ServerPath(fileSystem, "/", "//a//", "//b//", "//").toString());
		
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").isAbsolute());
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").isDirectory());
		Assert.assertFalse(new ServerPath(fileSystem, "a").isAbsolute());
		Assert.assertFalse(new ServerPath(fileSystem, "a").isDirectory());
		Assert.assertTrue(new ServerPath(fileSystem, "/").isAbsolute());
		Assert.assertTrue(new ServerPath(fileSystem, "/").isDirectory());
		Assert.assertFalse(new ServerPath(fileSystem, "").isAbsolute());
		Assert.assertFalse(new ServerPath(fileSystem, "").isDirectory());
	}
	
	@Test
	public void testResolve() {
		Assert.assertEquals("/", new ServerPath(fileSystem, "a").resolve("/").toString());
		Assert.assertEquals("a", new ServerPath(fileSystem, "a").resolve("").toString());

		Assert.assertEquals("/c/d", new ServerPath(fileSystem, "a/b").resolve("/c/d").toString());
		Assert.assertEquals("/c/d", new ServerPath(fileSystem, "/a/b").resolve("/c/d").toString());
		Assert.assertEquals("/c/d/", new ServerPath(fileSystem, "/a/b").resolve("/c/d/").toString());

		Assert.assertEquals("/a/b/c/d", new ServerPath(fileSystem, "/a/b").resolve("c/d").toString());
		Assert.assertEquals("/a/b/c/d", new ServerPath(fileSystem, "/a/b/").resolve("c/d").toString());
		Assert.assertEquals("/a/b/c/d/", new ServerPath(fileSystem, "/a/b/").resolve("c/d/").toString());
	}

	@Test
	public void testRelativize() {
		Assert.assertEquals("c/d/", new ServerPath(fileSystem, "/a/b").relativize(new ServerPath(fileSystem, "/a/b/c/d/")).toString());
		Assert.assertEquals("c/d", new ServerPath(fileSystem, "/a/b/").relativize(new ServerPath(fileSystem, "/a/b/c/d")).toString());

		Assert.assertEquals("../c", new ServerPath(fileSystem, "/a/b/").relativize(new ServerPath(fileSystem, "/a/c")).toString());
		Assert.assertEquals("../c/", new ServerPath(fileSystem, "/a/b").relativize(new ServerPath(fileSystem, "/a/c/")).toString());

		Assert.assertEquals("", new ServerPath(fileSystem, "/a/b").relativize(new ServerPath(fileSystem, "/a/b")).toString());
		Assert.assertEquals("", new ServerPath(fileSystem, "/a/b/").relativize(new ServerPath(fileSystem, "/a/b")).toString());
		Assert.assertEquals("/", new ServerPath(fileSystem, "/a/b").relativize(new ServerPath(fileSystem, "/a/b/")).toString());
	}
	
	boolean relativizeResolveCondition(String p, String q) {
		return new ServerPath(fileSystem, p).relativize(new ServerPath(fileSystem, p).resolve(q)).toString().equals(q);
	}

	@Test
	public void testRelativizeResolve() {
		Assert.assertTrue(relativizeResolveCondition("/a/b", "a"));
		Assert.assertTrue(relativizeResolveCondition("/a/b", "a/b"));
		Assert.assertTrue(relativizeResolveCondition("/a/b", "a/b/c"));
		Assert.assertTrue(relativizeResolveCondition("/a/b", "b/c"));
		Assert.assertTrue(relativizeResolveCondition("/a/b", "c"));

		Assert.assertTrue(relativizeResolveCondition("a/b", "a"));
		Assert.assertTrue(relativizeResolveCondition("a/b", "a/b"));
		Assert.assertTrue(relativizeResolveCondition("a/b", "a/b/c"));
		Assert.assertTrue(relativizeResolveCondition("a/b", "b/c"));
		Assert.assertTrue(relativizeResolveCondition("a/b", "c"));
	}
	
	@Test
	public void testNormalize() {
		Assert.assertEquals("a", new ServerPath(fileSystem, "./a").normalize().toString());
		Assert.assertEquals("/", new ServerPath(fileSystem, "/a/..").normalize().toString());
		Assert.assertEquals("../a", new ServerPath(fileSystem, "../a").normalize().toString());
		Assert.assertEquals("/../a", new ServerPath(fileSystem, "/../a").normalize().toString());
		Assert.assertEquals("../b/d", new ServerPath(fileSystem, "../a/./../b/c/../d").normalize().toString());
	}
	
	@Test
	public void testToAbsolutePath() {
		ServerPath path;
		
		path = new ServerPath(fileSystem, "/a");
		Assert.assertSame(path, path.toAbsolutePath());
		
		path = new ServerPath(fileSystem, "a");
		Assert.assertNotSame(path, path.toAbsolutePath());
		
		Assert.assertEquals("/foobar/a", new ServerPath(fileSystem, "a").toAbsolutePath().toString());
		Assert.assertEquals("/foobar/a/..", new ServerPath(fileSystem, "a/..").toAbsolutePath().toString());
		Assert.assertEquals("/foobar/../a", new ServerPath(fileSystem, "../a").toAbsolutePath().toString());
		Assert.assertEquals("/foobar/../a/./../b/c/../d", new ServerPath(fileSystem, "../a/./../b/c/../d").toAbsolutePath().toString());
	}

	@Test
	public void testToRealPath() throws IOException {
		Assert.assertEquals("/foobar/a", new ServerPath(fileSystem, "a").toRealPath().toString());
		Assert.assertEquals("/foobar/", new ServerPath(fileSystem, "a/..").toRealPath().toString());
		Assert.assertEquals("/a", new ServerPath(fileSystem, "../a").toRealPath().toString());
		Assert.assertEquals("/b/d", new ServerPath(fileSystem, "../a/./../b/c/../d").toRealPath().toString());
	}

	@Test
	public void testGetParent() {
		Assert.assertEquals("a/", new ServerPath(fileSystem, "a/b").getParent().toString());
		Assert.assertEquals("/a/", new ServerPath(fileSystem, "/a/b").getParent().toString());
		Assert.assertEquals(".", new ServerPath(fileSystem, "./a").getParent().toString());

		Assert.assertEquals("/", new ServerPath(fileSystem, "/a").getParent().toString());
		Assert.assertNull(new ServerPath(fileSystem, "a").getParent());
		Assert.assertNull(new ServerPath(fileSystem, "/").getParent());
	}
	
	@Test
	public void testCompareTo() {
		Assert.assertEquals( 0, new ServerPath(fileSystem, "a/bc").compareTo(new ServerPath(fileSystem, "a/bc")));
		Assert.assertEquals(-1, new ServerPath(fileSystem, "a/bc").compareTo(new ServerPath(fileSystem, "ab/c")));
		Assert.assertEquals( 1, new ServerPath(fileSystem, "ab/c").compareTo(new ServerPath(fileSystem, "a/bc")));

		Assert.assertEquals( 0, new ServerPath(fileSystem, "/abc").compareTo(new ServerPath(fileSystem, "abc")));
	}

	@Test
	public void testToUri() {
		ServerFileSystem fileSystem = new ServerFileSystem(URI.create("foo://bar:80/foobar/"));
		Assert.assertEquals("foo://bar:80/foobar/a", new ServerPath(fileSystem, "a").toUri().toString());

		fileSystem = new ServerFileSystem(URI.create("foobar/"));
		Assert.assertEquals("/foobar/a", new ServerPath(fileSystem, "a").toUri().toString());
		Assert.assertEquals("/a", new ServerPath(fileSystem, "../a").toUri().toString());
		Assert.assertEquals("/../a", new ServerPath(fileSystem, "../../a").toUri().toString());
	}

	@Test
	public void testStartsWith() {
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").startsWith("/"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").startsWith("/a"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").startsWith("/a/"));
		Assert.assertFalse(new ServerPath(fileSystem, "/a/").startsWith("/a/b"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a").startsWith("/a"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a").startsWith("/a/"));
		Assert.assertFalse(new ServerPath(fileSystem, "/a").startsWith("/a/b"));
	}

	@Test
	public void testEndsWith() {
		Assert.assertFalse(new ServerPath(fileSystem, "/a/").endsWith("/"));
		Assert.assertFalse(new ServerPath(fileSystem, "/a").endsWith("/"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").endsWith("/a"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/").endsWith("a"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/b").endsWith("b"));
		Assert.assertFalse(new ServerPath(fileSystem, "/a/b").endsWith("/b"));
		Assert.assertTrue(new ServerPath(fileSystem, "/a/b").endsWith("/a/b"));
		Assert.assertFalse(new ServerPath(fileSystem, "/ab").endsWith("b"));
	}
}
