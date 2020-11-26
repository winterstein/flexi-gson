package com.winterwell.gson;

import java.lang.reflect.Type;
import java.util.Map;

import org.junit.Test;

import com.winterwell.utils.AString;
import com.winterwell.utils.Key;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Pair;
import com.winterwell.utils.containers.Slice;
import com.winterwell.utils.time.Time;

public class RegexFirstAdapterTest {

	@Test
	public void testKey() {
		RegexFirstAdapter rfa = new RegexFirstAdapter<>(
				Key.class, 
				"\"k\":", "\"name\":");
		Gson gsonWith = new GsonBuilder()
						.registerTypeAdapterFactory(rfa)
						.create();		
//		rfa.setGson(gsonWith);
		Key p = new Key("foo");
		String gson1 = gsonWith.toJson(p);
		assert gson1.equals("{\"@class\":\"com.winterwell.utils.Key\",\"name\":\"foo\"}");
//		System.out.println(gson1);
		
		Key p2 = gsonWith.fromJson("{\"@class\":\"com.winterwell.utils.Key\",\"k\":\"foo\"}", Key.class);
		assert p2.name.equals("foo") : p2;
	}
	
}