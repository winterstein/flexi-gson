package com.winterwell.gson;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.tools.javac.util.Context.Key;
import com.winterwell.gson.internal.Streams;
import com.winterwell.gson.reflect.TypeToken;
import com.winterwell.gson.stream.JsonReader;
import com.winterwell.gson.stream.JsonWriter;
/**
 * 
 * @author daniel
 *
 * @param <X>
 * @testedby {@link RegexFirstAdapterTest}
 */
public final class RegexFirstAdapter<X> 
implements TypeAdapterFactory
//implements JsonDeserializer<X> // didn't work out
{

	private Pattern regex;
	private String replacement;
	private TypeAdapter adapter;
//	private Gson gson;

	public RegexFirstAdapter(Class<X> myType, String regex, String replacement) {
		this.regex = Pattern.compile(regex);
		this.replacement = replacement;
		this.myType = myType;
//		this.adapter = new ReflectiveTypeAdapter();
	}
	
	

	/**
	 * Test it does what we expect
	 * @param before
	 * @param after
	 * @return this
	 */
	public RegexFirstAdapter eg(String before, String after) {
		Matcher m = regex.matcher(before);
		String a = m.replaceAll(replacement);
		assert a.equals(after) : a;
		return this;
	}

////	@Override
//	public X deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {		
////		if (Utils.yes(loopFlag.get())) {
////			// in a loop
////			Object out = adapter.fromJsonTree(json);
////			return (X) out;
////		}
////		loopFlag.set(true);		
//		String s = json.toString();
//		Matcher m = regex.matcher(s);
//		String s2 = m.replaceAll(replacement);
//		JsonReader jsonReader = new JsonReader(new StringReader(s2));
//		JsonElement json2 = Streams.parse(jsonReader);
//		Object out = adapter.fromJsonTree(json2);		
////		Object x = context.deserialize(json2, typeOfT);
////		loopFlag.set(false);
//		return (X) out;
//	}



//	public void setGson(Gson gson) {
//		this.gson = gson;		
//	}


	Class myType = Key.class;

	@Override
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
		if ( ! myType.equals(type.getRawType())) {
			return null;
		}
//		setGson(gson);
		this.adapter = gson.getDelegateAdapter(this, type);
		return new RFA<T>();
	}

	private final class RFA<T2> extends TypeAdapter<T2> {

		@Override
		public void write(JsonWriter out, T2 value) throws IOException {
			adapter.write(out, value);
		}

		@Override
		public T2 read(JsonReader in) throws IOException {
			JsonElement parsed = Streams.parse(in);
			
			String s = parsed.toString();
			Matcher m = regex.matcher(s);
			String s2 = m.replaceAll(replacement);
			JsonReader jsonReader = new JsonReader(new StringReader(s2));			
			JsonElement json2 = Streams.parse(jsonReader);
			
			Object out = adapter.fromJsonTree(json2);
			return (T2) out;
		}
		
	}

}
