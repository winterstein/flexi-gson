package com.winterwell.gson.internal;

import java.util.List;

import org.junit.Test;

import com.winterwell.utils.Utils;

public class DefaultObjectConstructorTest {

	@Test
	public void testNoCons() throws NoSuchMethodException, SecurityException {
		DefaultObjectConstructor doc = new DefaultObjectConstructor(NoCons.class);
		NoCons foo = (NoCons) doc.construct();
		NoCons foo2 = (NoCons) doc.construct("blah");
		assert foo != null;
	}

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

class NoCons {
	int n = Utils.getRandom().nextInt();
	public NoCons(String foo) {
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