package com.winterwell.gson.internal;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class DefaultObjectConstructorTest {

	@Test
	public void testNoArgs() throws NoSuchMethodException, SecurityException {
		DefaultObjectConstructor doc = new DefaultObjectConstructor(NoArgs.class);
		NoArgs foo = (NoArgs) doc.construct();
		assert foo != null;
	}

	@Test
	public void testStringArg() throws NoSuchMethodException, SecurityException {
		DefaultObjectConstructor doc = new DefaultObjectConstructor(StringArg.class);
		StringArg foo = (StringArg) doc.construct("Hello");
		assert foo != null;
	}


	@Test(expected = Exception.class)
	public void testShouldFail() throws NoSuchMethodException, SecurityException {
		DefaultObjectConstructor doc = new DefaultObjectConstructor(List.class);
		Object foo = doc.construct();
		assert foo != null;
	}

}

class NoArgs {
}

class StringArg {
	private String s;

	public StringArg(String s) {
		this.s = s;
	}
}