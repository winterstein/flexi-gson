package com.winterwell.gson;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

import org.junit.Test;

import com.winterwell.gson.StandardAdapters.TimeTypeAdapter;
import com.winterwell.utils.AString;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Slice;
import com.winterwell.utils.time.TUnit;
import com.winterwell.utils.time.Time;

public class StandardAdaptersTest {


	@Test
	public void testSlice() {
		Gson gsonWith = new GsonBuilder()
				// NB: either of these work
						.registerTypeAdapter(Slice.class, new StandardAdapters.CharSequenceTypeAdapter(Slice.class))
//						.registerTypeAdapter(Slice.class, new StandardAdapters.ToStringSerialiser())
						.create();
		
		Slice slice = new Slice("Hello World", 1, 4);
		String gson1 = gsonWith.toJson(slice);
		System.out.println(gson1);
		assert gson1.equals("\"ell\"") : gson1;		
		Slice now2 = gsonWith.fromJson(gson1, Slice.class);
		assert slice.toString().equals(now2.toString());
		assert ! slice.equals(now2); // the start/end has been lost and base truncated
		assert slice.getBase().equals("Hello World");
		assert now2.getBase().equals("ell");
	}
	

	@Test
	public void testToString() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Class.class, new StandardAdapters.ClassTypeAdapter())
						.registerTypeAdapter(AString.class, new StandardAdapters.ToStringSerialiser())
						.create();
		
		AString foo = new AString("foo");
		String gson1 = gsonWith.toJson(foo);
		assert gson1.equals("\"foo\"") : gson1;
//		System.out.println(gson1);
		AString now2 = gsonWith.fromJson(gson1, AString.class);
		assert foo.equals(now2) : foo+" vs "+now2;
	}
	
	@Test
	public void testToStringSubClass() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Class.class, new StandardAdapters.ClassTypeAdapter())
						.registerTypeHierarchyAdapter(AString.class, new StandardAdapters.ToStringSerialiser())
						.create();
		
		TestString foo = new TestString("foo");
		String gson1 = gsonWith.toJson(foo);
		System.out.println(gson1);
		assert gson1.equals("\"foo\"") : gson1;
		TestString now2 = gsonWith.fromJson(gson1, TestString.class);
		assert foo.equals(now2);
	}
	
	static class TestString extends AString {
		public TestString(String name) {
			super(name);
		}
		private static final long serialVersionUID = 1L;
		
	}
	
	@Test
	public void testTime() {
		TimeTypeAdapter tta = new StandardAdapters.TimeTypeAdapter();
		tta.setLevel(TUnit.MILLISECOND);
		
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Time.class, tta)
						.create();
		
		Time now = new Time();
		String gson1 = gsonWith.toJson(now);
		System.out.println(now);
		System.out.println(gson1);
		Time now2 = gsonWith.fromJson(gson1, Time.class);
		System.out.println(now2);
		
		assert now.equals(now2) : now.getTime() - now2.getTime();
	}
	
	/**
	 * Explore test: Date serialises poorly (it drops milliseconds)
	 * But then: we don't have an adapter for it
	 */
	@Test
	public void testDate() {
		Gson gsonWith = new GsonBuilder()
				.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
				.create();

		Date now = new Date();
		String gson1 = Gson.toJSON(now);
		System.out.println(gson1);
		Date now2 = gsonWith.fromJson(gson1, Date.class);
		System.out.println(now.getTime() - now2.getTime());
		assert Math.abs(now.getTime() - now2.getTime()) < 1000;
//		assert now.equals(now2) : now2 +" vs "+now; Fails -- Date loses millisecond precision
	}


	@Test
	public void testCharSequence() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(StringBuilder.class, new StandardAdapters.CharSequenceTypeAdapter(StringBuilder.class))
						.create();
				
		String gson1 = Gson.toJSON(new StringBuilder("foo"));		
		System.out.println(gson1);
		
		String gson2 = gsonWith.toJSON(new StringBuilder("foo"));		
		System.out.println(gson2);
		
		StringBuilder n2 = gsonWith.fromJson(gson1, StringBuilder.class);
	}
	
	@Test
	public void testLenientLong() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Long.class, new StandardAdapters.LenientLongAdapter())
						.create();
		
		NumGen n1 = new NumGen();
		n1.a = 17.0000001;
		
		String gson1 = Gson.toJSON(n1);		
		System.out.println(gson1);
		
		NumLong n2 = gsonWith.fromJson(gson1, NumLong.class);
		assert n2.a == 17;
	}
	
	
	@Test
	public void testLenientlong() {
		Type longType = long.class;
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(longType, new StandardAdapters.LenientLongAdapter())
						.create();
		
		NumGen n1 = new NumGen();
		n1.a = 17.0000001;
		
		String gson1 = Gson.toJSON(n1);		
		System.out.println(gson1);
		
		Numlong n2 = gsonWith.fromJson(gson1, Numlong.class);
		assert n2.a == 17;
		
		// fails
//		NumLong n3 = gsonWith.fromJson(gson1, NumLong.class);
//		assert n3.a == 17;
	}
	
	@Test
	public void testLenientlongnull() {
		Type longType = long.class;
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(longType, new StandardAdapters.LenientLongAdapter(0L))
						.create();
		
		NumGen n1 = new NumGen();
		
		String gson1 = Gson.toJSON(n1);		
		System.out.println(gson1);
		// avoids an NPE
		Numlong n2 = gsonWith.fromJson(gson1, Numlong.class);
		assert n2.a == 0;		
	}
	
	@Test
	public void testLenientLonglong() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(long.class, new StandardAdapters.LenientLongAdapter())
						.registerTypeAdapter(Long.class, new StandardAdapters.LenientLongAdapter())
						.create();
		
		NumGen n1 = new NumGen();
		n1.a = 17.0000001;
		
		String gson1 = Gson.toJSON(n1);		
		System.out.println(gson1);
		
		Numlong n2 = gsonWith.fromJson(gson1, Numlong.class);
		assert n2.a == 17;
		
		NumLong n3 = gsonWith.fromJson(gson1, NumLong.class);
		assert n3.a == 17;
	}
	
	@Test
	public void testISOTime() {
		{
			Gson gsonWith = new GsonBuilder()
							.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
							.create();
						
			Time now2 = gsonWith.fromJson("\"2017-09-13T11:55:26Z\"", Time.class);
			
			assert now2 != null;
		}
		if (false) {	// without ""s it will fail, cos its not valid json
			Gson gsonWith = new GsonBuilder()
							.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
							.create();
						
			Time now2 = gsonWith.fromJson("2017-09-13T11:55:26Z", Time.class);
		}
	}
	
	@Test
	public void testWithMap() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Class.class, new StandardAdapters.ClassTypeAdapter())
						.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
						.create();
		
		ArrayMap map = new ArrayMap("myclass", getClass(), "mytime", new Time());
		
		System.out.println("MAP WITH");
		String gson1 = gsonWith.toJson(map);
		System.out.println(gson1);
		Map map1 = gsonWith.fromJson(gson1);
		System.out.println(map1);
		System.out.println(map1.get("myclass").getClass());
	}

	@Test
	public void testWithoutMap() {
		Gson gsonwo = new GsonBuilder().create();
		
		ArrayMap map = new ArrayMap("myclass", getClass(), "mytime", new Time());
		
		System.out.println("MAP WITHOUT");
		String gson2 = gsonwo.toJson(map);
		System.out.println(gson2);
		Map map2 = gsonwo.fromJson(gson2);
		System.out.println(map2);
		System.out.println(map2.get("myclass").getClass());
	}


	@Test
	public void testWithObj() {
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapter(Class.class, new StandardAdapters.ClassTypeAdapter())
						.registerTypeAdapter(Time.class, new StandardAdapters.TimeTypeAdapter())
						.create();
		
		MyObj map = new MyObj(getClass(), new Time());
		
		System.out.println("OBJ WITH");
		String gson1 = gsonWith.toJson(map);
		System.out.println(gson1);
		MyObj map1 = gsonWith.fromJson(gson1);
		System.out.println(map1);
	}

	@Test
	public void testWithoutObj() {
		Gson gsonwo = new GsonBuilder().create();
		
		MyObj map = new MyObj(getClass(), new Time());
		
		System.out.println("WITHOUT");
		String gson2 = gsonwo.toJson(map);
		System.out.println(gson2);
		MyObj map2 = gsonwo.fromJson(gson2);
		System.out.println(map2);
	}
}


class NumGen {
	public Number a;
}

class Numlong {
	public long a;
}

class NumLong {
	public Long a;
}

class MyObj {

	private Class klass;
	private Time time;

	public MyObj(Class class1, Time time) {
		this.klass = class1;
		this.time = time;
	}
	
}