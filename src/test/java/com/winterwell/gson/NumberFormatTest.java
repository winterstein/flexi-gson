package com.winterwell.gson;

import org.junit.Test;

public class NumberFormatTest {

	@Test
	public void testNumberAsNumber() {
		String json = "{'dfoo':10}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.dfoo == 10;
	}
	

	@Test
	public void testNullAsNumber() {
		String json = "{'dfoo':null}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.dfoo == null;
	}
	
	@Test
	public void testBlankAsNumber() {
		String json = "{'dfoo':''}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.dfoo == null;
	}
	
	@Test
	public void testStringAsNumber() {
		String json = "{'dfoo':'1'}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.dfoo == 1;
	}
	
	
	

	@Test
	public void testNumberAsint() {
		String json = "{'ifoo':10}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.ifoo == 10;
	}
	

	@Test
	public void testNullAsint() {
		String json = "{'ifoo':null}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.ifoo == 0;
	}
	
//	@Test ??
	public void testBlankAsint() {
		String json = "{'ifoo':''}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.ifoo == 0;
	}
	
	@Test
	public void testNullAsInteger() {
		String json = "{'IntegerFoo':null}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.IntegerFoo == null;
	}
	
	@Test
	public void testBlankAsInteger() {
		String json = "{'IntegerFoo':''}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.IntegerFoo == null;
	}
	
	@Test
	public void testStringAsint() {
		String json = "{'ifoo':'1'}".replace('\'', '"');
		Gson gson = new Gson();
		Foo foo = gson.fromJson(json, Foo.class);
		assert foo.ifoo == 1;
	}
}

class Foo {
	Double dfoo;
	int ifoo;
	Integer IntegerFoo;
	String foo;
}