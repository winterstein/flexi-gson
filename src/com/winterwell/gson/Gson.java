/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.winterwell.gson;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.winterwell.gson.internal.$Gson$Preconditions;
import com.winterwell.gson.internal.ConstructorConstructor;
import com.winterwell.gson.internal.Excluder;
import com.winterwell.gson.internal.Primitives;
import com.winterwell.gson.internal.Streams;
import com.winterwell.gson.internal.bind.ArrayTypeAdapter;
import com.winterwell.gson.internal.bind.CollectionTypeAdapterFactory;
import com.winterwell.gson.internal.bind.DateTypeAdapter;
import com.winterwell.gson.internal.bind.EnumMapTypeAdapter;
import com.winterwell.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory;
import com.winterwell.gson.internal.bind.JsonTreeReader;
import com.winterwell.gson.internal.bind.JsonTreeWriter;
import com.winterwell.gson.internal.bind.LBRow;
import com.winterwell.gson.internal.bind.LateBinding;
import com.winterwell.gson.internal.bind.MapTypeAdapterFactory;
import com.winterwell.gson.internal.bind.ObjectTypeAdapter;
import com.winterwell.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.winterwell.gson.internal.bind.SqlDateTypeAdapter;
import com.winterwell.gson.internal.bind.TimeTypeAdapter;
import com.winterwell.gson.internal.bind.TypeAdapters;
import com.winterwell.gson.reflect.TypeToken;
import com.winterwell.gson.stream.JsonReader;
import com.winterwell.gson.stream.JsonToken;
import com.winterwell.gson.stream.JsonWriter;
import com.winterwell.gson.stream.MalformedJsonException;
import com.winterwell.utils.io.FileUtils;
import com.winterwell.utils.log.KErrorPolicy;

/**
 * This is the main class for using Gson. Gson is typically used by first
 * constructing a Gson instance and then invoking {@link #toJson(Object)} or
 * {@link #fromJson(String, Class)} methods on it.
 *
 * <p>
 * You can create a Gson instance by invoking {@code new Gson()} if the default
 * configuration is all you need. You can also use {@link GsonBuilder} to build
 * a Gson instance with various configuration options such as versioning
 * support, pretty printing, custom {@link JsonSerializer}s,
 * {@link JsonDeserializer}s, and {@link InstanceCreator}s.
 * </p>
 *
 * <p>
 * Here is an example of how Gson is used for a simple Class:
 *
 * <pre>
 * Gson gson = new Gson(); // Or use new GsonBuilder().create();
 * MyType target = new MyType();
 * String json = gson.toJson(target); // serializes target to Json
 * MyType target2 = gson.fromJson(json, MyType.class); // deserializes json into
 * // target2
 * </pre>
 * 
 * </p>
 *
 * <p>
 * If the object that your are serializing/deserializing is a
 * {@code ParameterizedType} (i.e. contains at least one type parameter and may
 * be an array) then you must use the {@link #toJson(Object, Type)} or
 * {@link #fromJson(String, Type)} method. Here is an example for serializing
 * and deserialing a {@code ParameterizedType}:
 *
 * <pre>
 * Type listType = new TypeToken&lt;List&lt;String&gt;&gt;() {
 * }.getType();
 * List&lt;String&gt; target = new LinkedList&lt;String&gt;();
 * target.add(&quot;blah&quot;);
 * 
 * Gson gson = new Gson();
 * String json = gson.toJson(target, listType);
 * List&lt;String&gt; target2 = gson.fromJson(json, listType);
 * </pre>
 * 
 * </p>
 *
 * <p>
 * See the <a href="https://sites.google.com/site/gson/gson-user-guide">Gson
 * User Guide</a> for a more complete set of examples.
 * </p>
 *
 * @see com.winterwell.gson.reflect.TypeToken
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 * @author Jesse Wilson
 * @author Daniel Winterstein
 */
public class Gson {
	
	/**
	 * POJO to map/list/primitive "json object" conversion .
	 * This does serialise -> deserialise
	 * @param src
	 * @return a "json object" copy of src
	 */
	public Map toJsonObject(Object src) {
		String json = toJson(src);
		// Don't use a flexi-gson convertor! Because we don't want to interpret class info.
		return Gson.fromJSON(json);
	}

	
	/**
	 * Allows for LATE setting of an adapter. 
	 * @deprecated You should use {@link GsonBuilder#registerTypeAdapter(Type, Object)} instead. 
	 * @param type
	 * @param typeAdapter
	 * @return this
	 */
	 public Gson registerTypeAdapter(Type type, Object typeAdapter) {
		    $Gson$Preconditions.checkArgument(typeAdapter instanceof JsonSerializer<?>
		        || typeAdapter instanceof JsonDeserializer<?>
		        || typeAdapter instanceof InstanceCreator<?>
		        || typeAdapter instanceof TypeAdapter<?>);
//		    if (typeAdapter instanceof InstanceCreator<?>) {
//		      instanceCreators.put(type, (InstanceCreator) typeAdapter);
//		    }
		    TypeToken<?> typeToken = TypeToken.get(type);
		    if (typeAdapter instanceof JsonSerializer<?> || typeAdapter instanceof JsonDeserializer<?>) {		      
		      factories.add(0,TreeTypeAdapter.newFactoryWithMatchRawType(typeToken, typeAdapter));
		    }
		    if (typeAdapter instanceof TypeAdapter<?>) {
		      factories.add(0,TypeAdapters.newFactory(typeToken, (TypeAdapter)typeAdapter));
		    }
		    typeTokenCache.remove(typeToken);
		    return this;
		  }
	 
	/**
	 * What version is this? And what version was it branched from?
	 * The format is W(Winterwell version)_G(original Google version).
	 * An S on the Google-version indicates "snapshot"
	 * 
	 * TODO update from https://github.com/google/gson v2.8.5
	 */
	public static String VERSION = "W1.4_G2.3.1S";
	
	static final boolean DEFAULT_JSON_NON_EXECUTABLE = false;

	private static final String JSON_NON_EXECUTABLE_PREFIX = ")]}'\n";

	/**
	 * This thread local guards against reentrant calls to getAdapter(). In
	 * certain object graphs, creating an adapter for a type may recursively
	 * require an adapter for the same type! Without intervention, the recursive
	 * lookup would stack overflow. We cheat by returning a proxy type adapter.
	 * The proxy is wired up once the initial adapter has been created.
	 */
	private final ThreadLocal<Map<TypeToken<?>, FutureTypeAdapter<?>>> calls = new ThreadLocal<Map<TypeToken<?>, FutureTypeAdapter<?>>>();

	private final Map<TypeToken<?>, TypeAdapter<?>> typeTokenCache = Collections
			.synchronizedMap(new HashMap<TypeToken<?>, TypeAdapter<?>>());

	private final List<TypeAdapterFactory> factories;
	private final ConstructorConstructor constructorConstructor;

	private final boolean serializeNulls;
	private final boolean htmlSafe;
	private final boolean generateNonExecutableJson;
	private final boolean prettyPrinting;

	final JsonDeserializationContext deserializationContext = new OneJsonDeserializationContext(this);

	final JsonSerializationContext serializationContext = new JsonSerializationContext() {
		public JsonElement serialize(Object src) {
			return toJsonTree(src);
		}

		public JsonElement serialize(Object src, Type typeOfSrc) {
			return toJsonTree(src, typeOfSrc);
		}
	};

	// Added by Daniel
	private final String classProperty;

	private boolean lenientReader;

	/**
	 * See GsonBuilder.setClassMapping()
	 */
	public final Map<String,Class> classForClass = new HashMap();

	/**
	 * How do we handle circular references? never null. HACK Should not be
	 * static!!!
	 * 
	 * @since September 2014, added by Daniel
	 */
	private static KLoopPolicy loopPolicy;
	
	/**
	 * How do we handle "@class" failures?
	 */
	private KErrorPolicy classErrorPolicy = KErrorPolicy.THROW_EXCEPTION;

	private List<Function<String, String>> preprocessors;


	/**
	 * How do we handle circular references? never null.
	 * @since September 2014, added by Daniel
	 */
	public static KLoopPolicy getLoopPolicy() {
		return loopPolicy;
	}

	/**
	 * @see GsonBuilder#setClassProperty(String)
	 * @return null if this is off.
	 */
	public String getClassProperty() {
		return classProperty;
	}

	/**
	 * Constructs a Gson object with default configuration. The default
	 * configuration has the following settings:
	 * <ul>
	 * <li>The JSON generated by <code>toJson</code> methods is in compact
	 * representation. This means that all the unneeded white-space is removed.
	 * You can change this behavior with {@link GsonBuilder#setPrettyPrinting()}
	 * .</li>
	 * <li>The generated JSON omits all the fields that are null. Note that
	 * nulls in arrays are kept as is since an array is an ordered list.
	 * Moreover, if a field is not null, but its generated JSON is empty, the
	 * field is kept. You can configure Gson to serialize null values by setting
	 * {@link GsonBuilder#serializeNulls()}.</li>
	 * <li>Gson provides default serialization and deserialization for Enums,
	 * {@link Map}, {@link java.net.URL}, {@link java.net.URI},
	 * {@link java.util.Locale}, {@link java.util.Date},
	 * {@link java.math.BigDecimal}, and {@link java.math.BigInteger} classes.
	 * If you would prefer to change the default representation, you can do so
	 * by registering a type adapter through
	 * {@link GsonBuilder#registerTypeAdapter(Type, Object)}.</li>
	 * <li>The default Date format is same as
	 * {@link java.text.DateFormat#DEFAULT}. This format ignores the millisecond
	 * portion of the date during serialization. You can change this by invoking
	 * {@link GsonBuilder#setDateFormat(int)} or
	 * {@link GsonBuilder#setDateFormat(String)}.</li>
	 * <li>By default, Gson ignores the
	 * {@link com.winterwell.gson.annotations.Expose} annotation. You can enable
	 * Gson to serialize/deserialize only those fields marked with this
	 * annotation through
	 * {@link GsonBuilder#excludeFieldsWithoutExposeAnnotation()}.</li>
	 * <li>By default, Gson ignores the
	 * {@link com.winterwell.gson.annotations.Since} annotation. You can enable Gson
	 * to use this annotation through {@link GsonBuilder#setVersion(double)}.</li>
	 * <li>The default field naming policy for the output Json is same as in
	 * Java. So, a Java class field <code>versionNumber</code> will be output as
	 * <code>&quot;versionNumber&quot;</code> in Json. The same rules are
	 * applied for mapping incoming Json to the Java classes. You can change
	 * this policy through
	 * {@link GsonBuilder#setFieldNamingPolicy(FieldNamingPolicy)}.</li>
	 * <li>By default, Gson excludes <code>transient</code> or
	 * <code>static</code> fields from consideration for serialization and
	 * deserialization. You can change this behavior through
	 * {@link GsonBuilder#excludeFieldsWithModifiers(int...)}.</li>
	 * </ul>
	 */
	public Gson() {
		this(Excluder.DEFAULT, FieldNamingPolicy.IDENTITY, Collections
				.<Type, InstanceCreator<?>> emptyMap(), false, false,
				DEFAULT_JSON_NON_EXECUTABLE, true, false, false,
				LongSerializationPolicy.DEFAULT,
				GsonBuilder.DEFAULT_CLASS_PROPERTY, 
				null/* loop policy */,
				false,
				Collections.<TypeAdapterFactory> emptyList(), 
				Collections.EMPTY_MAP, 
				null
				);
	}

	/**
	 * @param classProperty
	 *            ^Daniel
	 * @param classForClass 
	 * @param preprocessors 
	 * @param loopChecking
	 */
	Gson(final Excluder excluder, final FieldNamingStrategy fieldNamingPolicy,
			final Map<Type, InstanceCreator<?>> instanceCreators,
			boolean serializeNulls, boolean complexMapKeySerialization,
			boolean generateNonExecutableGson, boolean htmlSafe,
			boolean prettyPrinting,
			boolean serializeSpecialFloatingPointValues,
			LongSerializationPolicy longSerializationPolicy,
			String classProperty, KLoopPolicy loopPolicy,
			boolean lenientReader,
			List<TypeAdapterFactory> typeAdapterFactories, Map<String, Class> classForClass, 
			List<Function<String, String>> preprocessors)
    {
		this.constructorConstructor = new ConstructorConstructor(
				instanceCreators, classProperty);
		this.serializeNulls = serializeNulls;
		this.generateNonExecutableJson = generateNonExecutableGson;
		this.htmlSafe = htmlSafe;
		this.prettyPrinting = prettyPrinting;
		this.classProperty = classProperty;
		if (classForClass!=null) this.classForClass.putAll(classForClass);
		this.loopPolicy = loopPolicy == null ? KLoopPolicy.NO_CHECKS
				: loopPolicy;
		this.lenientReader = lenientReader;
		this.preprocessors = preprocessors;

		List<TypeAdapterFactory> factories = new ArrayList<TypeAdapterFactory>();

		// built-in type adapters that cannot be overridden
		factories.add(TypeAdapters.JSON_ELEMENT_FACTORY);

		// (Winterwell ^DBW) This map-making default factory kicks in a bit too often.
		// Switch it off in favour of ReflectiveTypeAdapterFactory if we're using @class properties
		if (classProperty == null) {
			factories.add(ObjectTypeAdapter.FACTORY);
		}

		// the excluder must precede all adapters that handle user-defined types
		factories.add(excluder);
		
		// user's type adapters
		factories.addAll(typeAdapterFactories);

		// type adapters for basic platform types
		factories.add(TypeAdapters.STRING_FACTORY);
		factories.add(TypeAdapters.INTEGER_FACTORY);
		factories.add(TypeAdapters.BOOLEAN_FACTORY);
		factories.add(TypeAdapters.BYTE_FACTORY);
		factories.add(TypeAdapters.SHORT_FACTORY);
		factories.add(TypeAdapters.newFactory(long.class, Long.class,
				longAdapter(longSerializationPolicy)));
		factories.add(TypeAdapters.newFactory(double.class, Double.class,
				doubleAdapter(serializeSpecialFloatingPointValues)));
		factories.add(TypeAdapters.newFactory(float.class, Float.class,
				floatAdapter(serializeSpecialFloatingPointValues)));
		factories.add(TypeAdapters.NUMBER_FACTORY);
		factories.add(TypeAdapters.CHARACTER_FACTORY);
		factories.add(TypeAdapters.STRING_BUILDER_FACTORY);
		factories.add(TypeAdapters.STRING_BUFFER_FACTORY);
		factories.add(TypeAdapters.newFactory(BigDecimal.class,
				TypeAdapters.BIG_DECIMAL));
		factories.add(TypeAdapters.newFactory(BigInteger.class,
				TypeAdapters.BIG_INTEGER));
		factories.add(TypeAdapters.URL_FACTORY);
		factories.add(TypeAdapters.URI_FACTORY);
		factories.add(TypeAdapters.UUID_FACTORY);
		factories.add(TypeAdapters.LOCALE_FACTORY);
		factories.add(TypeAdapters.INET_ADDRESS_FACTORY);
		factories.add(TypeAdapters.BIT_SET_FACTORY);
		factories.add(DateTypeAdapter.FACTORY);
		factories.add(TypeAdapters.CALENDAR_FACTORY);
		factories.add(TimeTypeAdapter.FACTORY);
		factories.add(SqlDateTypeAdapter.FACTORY);
		factories.add(TypeAdapters.TIMESTAMP_FACTORY);
		factories.add(ArrayTypeAdapter.FACTORY);
		factories.add(TypeAdapters.ENUM_FACTORY);
        // (Winterwell ^DBW)
        if (classProperty!=null) {
            // special support for EnumMap only makes sense if we can also store the class info
            factories.add(TypeAdapters.newFactory(EnumMap.class, new EnumMapTypeAdapter(this)));
        }
		factories.add(TypeAdapters.CLASS_FACTORY);

		// type adapters for composite and user-defined types
		factories.add(new CollectionTypeAdapterFactory(constructorConstructor));
		factories.add(new MapTypeAdapterFactory(constructorConstructor,
				complexMapKeySerialization));
		factories.add(new JsonAdapterAnnotationTypeAdapterFactory(
				constructorConstructor));
		
		// (Winterwell DBW) experiment!
//		if (classProperty!=null) {
//			factories.add(new RuntimeTypeAdapterFactory(classProperty));
//		}

//		// This is modified to handle the @class magic and loop-policy ^DBW
		factories.add(new ReflectiveTypeAdapterFactory(constructorConstructor,
				fieldNamingPolicy, excluder, classProperty));

		this.factories = 
				// allow for late editing :(
//				Collections.unmodifiableList(
						factories;
//						);
	}

	private TypeAdapter<Double> doubleAdapter(
			boolean serializeSpecialFloatingPointValues) {
		if (serializeSpecialFloatingPointValues) {
			return TypeAdapters.DOUBLE;
		}
		// Almost identical to TypeAdapters.DOUBLE! Just adds an extra check on write
		return new TypeAdapter<Double>() {
			@Override
			public Double read(JsonReader in) throws IOException {
				return TypeAdapters.DOUBLE.read(in);
			}

			@Override
			public void write(JsonWriter out, Double value) throws IOException {
				if (value == null) {
					out.nullValue();
					return;
				}
				double doubleValue = value.doubleValue();
				checkValidFloatingPoint(doubleValue);
				out.value(value);
			}
		};
	}

	private TypeAdapter<Float> floatAdapter(
			boolean serializeSpecialFloatingPointValues) {
		if (serializeSpecialFloatingPointValues) {
			return TypeAdapters.FLOAT;
		}
		return new TypeAdapter<Float>() {
			@Override
			public Float read(JsonReader in) throws IOException {
				return TypeAdapters.FLOAT.read(in);
			}

			@Override
			public void write(JsonWriter out, Float value) throws IOException {
				if (value == null) {
					out.nullValue();
					return;
				}
				float floatValue = value.floatValue();
				checkValidFloatingPoint(floatValue);
				out.value(value);
			}
		};
	}

	private void checkValidFloatingPoint(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new IllegalArgumentException(
					value
							+ " is not a valid double value as per JSON specification. To override this"
							+ " behavior, use GsonBuilder.serializeSpecialFloatingPointValues() method.");
		}
	}

	private TypeAdapter<Number> longAdapter(
			LongSerializationPolicy longSerializationPolicy) {
		if (longSerializationPolicy == LongSerializationPolicy.DEFAULT) {
			return TypeAdapters.LONG;
		}
		return new TypeAdapter<Number>() {
			@Override
			public Number read(JsonReader in) throws IOException {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					return null;
				}
				return in.nextLong();
			}

			@Override
			public void write(JsonWriter out, Number value) throws IOException {
				if (value == null) {
					out.nullValue();
					return;
				}
				out.value(value.toString());
			}
		};
	}

	/**
	 * Returns the type adapter for {@code} type.
	 *
	 * @throws IllegalArgumentException
	 *             if this GSON cannot serialize and deserialize {@code type}.
	 */
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> getAdapter(TypeToken<T> type) {
		TypeAdapter<?> cached = typeTokenCache.get(type);
		if (cached != null) {
			return (TypeAdapter<T>) cached;
		}

		Map<TypeToken<?>, FutureTypeAdapter<?>> threadCalls = calls.get();
		boolean requiresThreadLocalCleanup = false;
		if (threadCalls == null) {
			threadCalls = new HashMap<TypeToken<?>, FutureTypeAdapter<?>>();
			calls.set(threadCalls);
			requiresThreadLocalCleanup = true;
		}

		// the key and value type parameters always agree
		FutureTypeAdapter<T> ongoingCall = (FutureTypeAdapter<T>) threadCalls
				.get(type);
		if (ongoingCall != null) {
			return ongoingCall;
		}

		try {
			FutureTypeAdapter<T> call = new FutureTypeAdapter<T>();
			threadCalls.put(type, call);

			for (TypeAdapterFactory factory : factories) {
				TypeAdapter<T> candidate = factory.create(this, type);
				if (candidate != null) {
					call.setDelegate(candidate);
					typeTokenCache.put(type, candidate);
					return candidate;
				}
			}
			throw new IllegalArgumentException("GSON cannot handle " + type);
		} finally {
			threadCalls.remove(type);

			if (requiresThreadLocalCleanup) {
				calls.remove();
			}
		}
	}

	/**
	 * This method is used to get an alternate type adapter for the specified
	 * type. This is used to access a type adapter that is overridden by a
	 * {@link TypeAdapterFactory} that you may have registered. This features is
	 * typically used when you want to register a type adapter that does a
	 * little bit of work but then delegates further processing to the Gson
	 * default type adapter. Here is an example:
	 * <p>
	 * Let's say we want to write a type adapter that counts the number of
	 * objects being read from or written to JSON. We can achieve this by
	 * writing a type adapter factory that uses the
	 * <code>getDelegateAdapter</code> method:
	 * 
	 * <pre>
	 * {
	 * 	&#064;code
	 * 	class StatsTypeAdapterFactory implements TypeAdapterFactory {
	 * 		public int numReads = 0;
	 * 		public int numWrites = 0;
	 * 
	 * 		public &lt;T&gt; TypeAdapter&lt;T&gt; create(Gson gson, TypeToken&lt;T&gt; type) {
	 * 			final TypeAdapter&lt;T&gt; delegate = gson.getDelegateAdapter(this, type);
	 * 			return new TypeAdapter&lt;T&gt;() {
	 * 				public void write(JsonWriter out, T value) throws IOException {
	 * 					++numWrites;
	 * 					delegate.write(out, value);
	 * 				}
	 * 
	 * 				public T read(JsonReader in) throws IOException {
	 * 					++numReads;
	 * 					return delegate.read(in);
	 * 				}
	 * 			};
	 * 		}
	 * 	}
	 * }
	 * </pre>
	 * 
	 * This factory can now be used like this:
	 * 
	 * <pre>
	 * {
	 * 	&#064;code
	 * 	StatsTypeAdapterFactory stats = new StatsTypeAdapterFactory();
	 * 	Gson gson = new GsonBuilder().registerTypeAdapterFactory(stats).create();
	 * 	// Call gson.toJson() and fromJson methods on objects
	 * 	System.out.println(&quot;Num JSON reads&quot; + stats.numReads);
	 * 	System.out.println(&quot;Num JSON writes&quot; + stats.numWrites);
	 * }
	 * </pre>
	 * 
	 * Note that since you can not override type adapter factories for String
	 * and Java primitive types, our stats factory will not count the number of
	 * String or primitives that will be read or written.
	 * 
	 * @param skipPast
	 *            The type adapter factory that needs to be skipped while
	 *            searching for a matching type adapter. In most cases, you
	 *            should just pass <i>this</i> (the type adapter factory from
	 *            where {@link #getDelegateAdapter} method is being invoked).
	 * @param type
	 *            Type for which the delegate adapter is being searched for.
	 *
	 * @since 2.2
	 */
	public <T> TypeAdapter<T> getDelegateAdapter(TypeAdapterFactory skipPast,
			TypeToken<T> type) {
		boolean skipPastFound = false;

		for (TypeAdapterFactory factory : factories) {
			if (!skipPastFound) {
				if (factory == skipPast) {
					skipPastFound = true;
				}
				continue;
			}

			TypeAdapter<T> candidate = factory.create(this, type);
			if (candidate != null) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("GSON cannot serialize " + type);
	}

	/**
	 * Returns the type adapter for {@code} type.
	 *
	 * @throws IllegalArgumentException
	 *             if this GSON cannot serialize and deserialize {@code type}.
	 */
	public <T> TypeAdapter<T> getAdapter(Class<T> type) {
		return getAdapter(TypeToken.get(type));
	}

	/**
	 * This method serializes the specified object into its equivalent
	 * representation as a tree of {@link JsonElement}s. This method should be
	 * used when the specified object is not a generic type. This method uses
	 * {@link Class#getClass()} to get the type for the specified object, but
	 * the {@code getClass()} loses the generic type information because of the
	 * Type Erasure feature of Java. Note that this method works fine if the any
	 * of the object fields are of generic type, just the object itself should
	 * not be of a generic type. If the object is of generic type, use
	 * {@link #toJsonTree(Object, Type)} instead.
	 *
	 * @param src
	 *            the object for which Json representation is to be created
	 *            setting for Gson
	 * @return Json representation of {@code src}.
	 * @since 1.4
	 */
	public JsonElement toJsonTree(Object src) {
		if (src == null) {
			return JsonNull.INSTANCE;
		}
		return toJsonTree(src, src.getClass());
	}

	/**
	 * This method serializes the specified object, including those of generic
	 * types, into its equivalent representation as a tree of
	 * {@link JsonElement}s. This method must be used if the specified object is
	 * a generic type. For non-generic objects, use {@link #toJsonTree(Object)}
	 * instead.
	 *
	 * @param src
	 *            the object for which JSON representation is to be created
	 * @param typeOfSrc
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @return Json representation of {@code src}
	 * @since 1.4
	 */
	public JsonElement toJsonTree(Object src, Type typeOfSrc) {
		JsonTreeWriter writer = new JsonTreeWriter();
		toJson(src, typeOfSrc, writer);
		return writer.get();
	}

	/**
	 * This method serializes the specified object into its equivalent Json
	 * representation. This method should be used when the specified object is
	 * not a generic type. This method uses {@link Class#getClass()} to get the
	 * type for the specified object, but the {@code getClass()} loses the
	 * generic type information because of the Type Erasure feature of Java.
	 * Note that this method works fine if the any of the object fields are of
	 * generic type, just the object itself should not be of a generic type. If
	 * the object is of generic type, use {@link #toJson(Object, Type)} instead.
	 * If you want to write out the object to a {@link Writer}, use
	 * {@link #toJson(Object, Appendable)} instead.
	 *
	 * @param src
	 *            the object for which Json representation is to be created
	 *            setting for Gson
	 * @return Json representation of {@code src}.
	 */
	public String toJson(Object src) {
		if (src == null) {
			return toJson(JsonNull.INSTANCE);
		}
		return toJson(src, src.getClass());
	}

	/**
	 * This method serializes the specified object, including those of generic
	 * types, into its equivalent Json representation. This method must be used
	 * if the specified object is a generic type. For non-generic objects, use
	 * {@link #toJson(Object)} instead. If you want to write out the object to a
	 * {@link Appendable}, use {@link #toJson(Object, Type, Appendable)}
	 * instead.
	 *
	 * @param src
	 *            the object for which JSON representation is to be created
	 * @param typeOfSrc
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @return Json representation of {@code src}
	 */
	public String toJson(Object src, Type typeOfSrc) {
		StringWriter writer = new StringWriter();
		toJson(src, typeOfSrc, writer);
		return writer.toString();
	}

	/**
	 * This method serializes the specified object into its equivalent Json
	 * representation. This method should be used when the specified object is
	 * not a generic type. This method uses {@link Class#getClass()} to get the
	 * type for the specified object, but the {@code getClass()} loses the
	 * generic type information because of the Type Erasure feature of Java.
	 * Note that this method works fine if the any of the object fields are of
	 * generic type, just the object itself should not be of a generic type. If
	 * the object is of generic type, use
	 * {@link #toJson(Object, Type, Appendable)} instead.
	 *
	 * @param src
	 *            the object for which Json representation is to be created
	 *            setting for Gson
	 * @param writer
	 *            Writer to which the Json representation needs to be written
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 * @since 1.2
	 */
	public void toJson(Object src, Appendable writer) throws JsonIOException {
		if (src != null) {
			toJson(src, src.getClass(), writer);
		} else {
			toJson(JsonNull.INSTANCE, writer);
		}
	}

	/**
	 * This method serializes the specified object, including those of generic
	 * types, into its equivalent Json representation. This method must be used
	 * if the specified object is a generic type. For non-generic objects, use
	 * {@link #toJson(Object, Appendable)} instead.
	 *
	 * @param src
	 *            the object for which JSON representation is to be created
	 * @param typeOfSrc
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfSrc = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @param writer
	 *            Writer to which the Json representation of src needs to be
	 *            written.
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 * @since 1.2
	 */
	public void toJson(Object src, Type typeOfSrc, Appendable writer)
			throws JsonIOException {
		try {
			JsonWriter jsonWriter = newJsonWriter(Streams
					.writerForAppendable(writer));
			toJson(src, typeOfSrc, jsonWriter);
		} catch (IOException e) {
			throw new JsonIOException(e);
		}
	}

	/**
	 * Writes the JSON representation of {@code src} of type {@code typeOfSrc}
	 * to {@code writer}.
	 * 
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 */
	@SuppressWarnings("unchecked")
	public void toJson(Object src, Type typeOfSrc, JsonWriter writer)
			throws JsonIOException {
		TypeAdapter<?> adapter = getAdapter(TypeToken.get(typeOfSrc));
		boolean oldLenient = writer.isLenient();
		writer.setLenient(true);
		boolean oldHtmlSafe = writer.isHtmlSafe();
		writer.setHtmlSafe(htmlSafe);
		boolean oldSerializeNulls = writer.getSerializeNulls();
		writer.setSerializeNulls(serializeNulls);
		try {
			((TypeAdapter<Object>) adapter).write(writer, src);
		} catch (IOException e) {
			throw new JsonIOException(e);
		} finally {
			writer.setLenient(oldLenient);
			writer.setHtmlSafe(oldHtmlSafe);
			writer.setSerializeNulls(oldSerializeNulls);
		}
	}

	/**
	 * Converts a tree of {@link JsonElement}s into its equivalent JSON
	 * representation.
	 *
	 * @param jsonElement
	 *            root of a tree of {@link JsonElement}s
	 * @return JSON String representation of the tree
	 * @since 1.4
	 */
	public String toJson(JsonElement jsonElement) {
		StringWriter writer = new StringWriter();
		toJson(jsonElement, writer);
		return writer.toString();
	}

	/**
	 * Writes out the equivalent JSON for a tree of {@link JsonElement}s.
	 *
	 * @param jsonElement
	 *            root of a tree of {@link JsonElement}s
	 * @param writer
	 *            Writer to which the Json representation needs to be written
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 * @since 1.4
	 */
	public void toJson(JsonElement jsonElement, Appendable writer)
			throws JsonIOException {
		try {
			JsonWriter jsonWriter = newJsonWriter(Streams
					.writerForAppendable(writer));
			toJson(jsonElement, jsonWriter);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a new JSON writer configured for this GSON and with the
	 * non-execute prefix if that is configured.
	 */
	private JsonWriter newJsonWriter(Writer writer) throws IOException {
		if (generateNonExecutableJson) {
			writer.write(JSON_NON_EXECUTABLE_PREFIX);
		}
		JsonWriter jsonWriter = new JsonWriter(writer);
		if (prettyPrinting) {
			jsonWriter.setIndent("  ");
		}
		jsonWriter.setSerializeNulls(serializeNulls);
		return jsonWriter;
	}

	/**
	 * Writes the JSON for {@code jsonElement} to {@code writer}.
	 * 
	 * @throws JsonIOException
	 *             if there was a problem writing to the writer
	 */
	public void toJson(JsonElement jsonElement, JsonWriter writer)
			throws JsonIOException {
		boolean oldLenient = writer.isLenient();
		writer.setLenient(true);
		boolean oldHtmlSafe = writer.isHtmlSafe();
		writer.setHtmlSafe(htmlSafe);
		boolean oldSerializeNulls = writer.getSerializeNulls();
		writer.setSerializeNulls(serializeNulls);
		try {
			Streams.write(jsonElement, writer);
		} catch (IOException e) {
			throw new JsonIOException(e);
		} finally {
			writer.setLenient(oldLenient);
			writer.setHtmlSafe(oldHtmlSafe);
			writer.setSerializeNulls(oldSerializeNulls);
		}
	}

	/**
	 * This method deserializes the specified Json into an object of the
	 * specified class. It is not suitable to use if the specified class is a
	 * generic type since it will not have the generic type information because
	 * of the Type Erasure feature of Java. Therefore, this method should not be
	 * used if the desired type is a generic type. Note that this method works
	 * fine if the any of the fields of the specified object are generics, just
	 * the object itself should not be a generic type. For the cases when the
	 * object is of generic type, invoke {@link #fromJson(String, Type)}. If you
	 * have the Json in a {@link Reader} instead of a String, use
	 * {@link #fromJson(Reader, Class)} instead.
	 *
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the string from which the object is to be deserialized
	 * @param classOfT
	 *            the class of T
	 * @return an object of type T from the string. Returns {@code null} if
	 *         {@code json} is {@code null}.
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 *             classOfT
	 */
	public <T> T fromJson(String json, Class<T> classOfT)
			throws JsonSyntaxException {
		Object object = fromJson(json, (Type) classOfT);
		return Primitives.wrap(classOfT).cast(object);
	}
	

	/**
	 * This method deserializes the specified Json into an object of the
	 * specified type. This method is useful if the specified object is a
	 * generic type. For non-generic objects, use
	 * {@link #fromJson(String, Class)} instead. If you have the Json in a
	 * {@link Reader} instead of a String, use {@link #fromJson(Reader, Type)}
	 * instead.
	 *
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the string from which the object is to be deserialized
	 * @param typeOfT
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @return an object of type T from the string. Returns {@code null} if
	 *         {@code json} is {@code null}.
	 * @throws JsonParseException
	 *             if json is not a valid representation for an object of type
	 *             typeOfT
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 */
	@SuppressWarnings("unchecked")
	public <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
		if (json == null) {
			return null;
		}
		StringReader reader = new StringReader(json);
		T target = (T) fromJson(reader, typeOfT);
		return target;
	}

	/**
	 * This method deserializes the Json read from the specified reader into an
	 * object of the specified class. It is not suitable to use if the specified
	 * class is a generic type since it will not have the generic type
	 * information because of the Type Erasure feature of Java. Therefore, this
	 * method should not be used if the desired type is a generic type. Note
	 * that this method works fine if the any of the fields of the specified
	 * object are generics, just the object itself should not be a generic type.
	 * For the cases when the object is of generic type, invoke
	 * {@link #fromJson(Reader, Type)}. If you have the Json in a String form
	 * instead of a {@link Reader}, use {@link #fromJson(String, Class)}
	 * instead.
	 *
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the reader producing the Json from which the object is to be
	 *            deserialized.
	 * @param classOfT
	 *            the class of T
	 * @return an object of type T from the string. Returns {@code null} if
	 *         {@code json} is at EOF.
	 * @throws JsonIOException
	 *             if there was a problem reading from the Reader
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 * @since 1.2
	 */
	public <T> T fromJson(Reader json, Class<T> classOfT)
			throws JsonSyntaxException, JsonIOException {
		JsonReader jsonReader = new JsonReader(json);
		Object object = fromJson(jsonReader, classOfT);
		assertFullConsumption(object, jsonReader);
		return Primitives.wrap(classOfT).cast(object);
	}

	/**
	 * This method deserializes the Json read from the specified reader into an
	 * object of the specified type. This method is useful if the specified
	 * object is a generic type. For non-generic objects, use
	 * {@link #fromJson(Reader, Class)} instead. If you have the Json in a
	 * String form instead of a {@link Reader}, use
	 * {@link #fromJson(String, Type)} instead.
	 *
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the reader producing Json from which the object is to be
	 *            deserialized
	 * @param typeOfT
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @return an object of type T from the json. Returns {@code null} if
	 *         {@code json} is at EOF.
	 * @throws JsonIOException
	 *             if there was a problem reading from the Reader
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 * @since 1.2
	 */
	@SuppressWarnings("unchecked")
	public <T> T fromJson(Reader json, Type typeOfT) throws JsonIOException,
			JsonSyntaxException {
		// preprocessor?
		if (preprocessors!=null) {
			// bleurgh - have to unstream
			String sjson = FileUtils.read(json);
			for(Function<String, String> preprocessor : preprocessors) {
				sjson = preprocessor.apply(sjson);
			}
			StringReader json2 = new StringReader(sjson);
			json = json2;
		}
		JsonReader jsonReader = new JsonReader(json);
		if (lenientReader) {
			jsonReader.setLenient(true);
		}
		T object = (T) fromJson(jsonReader, typeOfT);
		
		try {
			for(LBRow lbs : jsonReader.getLateBindings()) {
				Object obj = lbs.obj;
				Field f = lbs.f;
				int i = lbs.index;
				LateBinding lb = lbs.lb;
				Object value = jsonReader.getIdValue(lb.ref);
				if (value==null) {
					throw new JsonSyntaxException("Could not resolve ref: "+lb);
				}
				if (f!=null) {
					f.set(obj, value);
				} else if (obj instanceof List) {
					((List) obj).set(i, value);
				} else if (obj.getClass().isArray()) {
					Array.set(obj, i, value);
				} else if (obj instanceof Set) {
					((Set) obj).add(value);
				} else {
					throw new JsonSyntaxException("TODO "+obj.getClass());
				}
			}
		} catch(JsonSyntaxException ex) {
			throw ex;
		} catch(Exception ex) {
			throw new JsonSyntaxException("Bad JSOG id/ref: "+ex, ex);
		}
		
		assertFullConsumption(object, jsonReader);
		return object;
	}

	private static void assertFullConsumption(Object obj, JsonReader reader) {
		try {
			if (obj != null && reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonIOException(
						"JSON document was not fully consumed.");
			}
		} catch (MalformedJsonException e) {
			throw new JsonSyntaxException(e);
		} catch (IOException e) {
			throw new JsonIOException(e);
		}
	}

	/**
	 * Reads the next JSON value from {@code reader} and convert it to an object
	 * of type {@code typeOfT}. Returns {@code null}, if the {@code reader} is
	 * at EOF. Since Type is not parameterized by T, this method is type unsafe
	 * and should be used carefully
	 *
	 * @throws JsonIOException
	 *             if there was a problem writing to the Reader
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 */
	@SuppressWarnings("unchecked")
	public <T> T fromJson(JsonReader reader, Type typeOfT)
			throws JsonIOException, JsonSyntaxException {
		boolean isEmpty = true;
		boolean oldLenient = reader.isLenient();
		reader.setLenient(true);
		JsonToken peekType = null;
		try {
			peekType = reader.peek();
			if (peekType==JsonToken.NULL) { // paranoia
				reader.nextNull();
				return null;
			}
			isEmpty = false;
			TypeToken<T> typeToken = (TypeToken<T>) TypeToken.get(typeOfT);
			TypeAdapter<T> typeAdapter = getAdapter(typeToken);
			T object = typeAdapter.read(reader);
			return object;
		} catch (EOFException e) {
			// NB: We need a try-catch as reader.hasNext() would also trigger an exception at EOF
			/*
			 * For compatibility with JSON 1.5 and earlier, we return null for
			 * empty documents instead of throwing.
			 */
			if (isEmpty) {
				return null;
			}
			throw new JsonSyntaxException(e);
		} catch (IllegalStateException e) {
			throw new JsonSyntaxException(e);
		} catch (IOException e) {
			e.printStackTrace(); // DEBUG
			// TODO(inder): Figure out whether it is indeed right to rethrow
			// this as JsonSyntaxException
			throw new JsonSyntaxException(e);
		} finally {
			reader.setLenient(oldLenient);
		}
	}

	/**
	 * This method deserializes the Json read from the specified parse tree into
	 * an object of the specified type. It is not suitable to use if the
	 * specified class is a generic type since it will not have the generic type
	 * information because of the Type Erasure feature of Java. Therefore, this
	 * method should not be used if the desired type is a generic type. Note
	 * that this method works fine if the any of the fields of the specified
	 * object are generics, just the object itself should not be a generic type.
	 * For the cases when the object is of generic type, invoke
	 * {@link #fromJson(JsonElement, Type)}.
	 * 
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the root of the parse tree of {@link JsonElement}s from which
	 *            the object is to be deserialized
	 * @param classOfT
	 *            The class of T
	 * @return an object of type T from the json. Returns {@code null} if
	 *         {@code json} is {@code null}.
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 *             typeOfT
	 * @since 1.3
	 */
	public <T> T fromJson(JsonElement json, Class<T> classOfT)
			throws JsonSyntaxException {
		Object object = fromJson(json, (Type) classOfT);
		return Primitives.wrap(classOfT).cast(object);
	}

	/**
	 * This method deserializes the Json read from the specified parse tree into
	 * an object of the specified type. This method is useful if the specified
	 * object is a generic type. For non-generic objects, use
	 * {@link #fromJson(JsonElement, Class)} instead.
	 *
	 * @param <T>
	 *            the type of the desired object
	 * @param json
	 *            the root of the parse tree of {@link JsonElement}s from which
	 *            the object is to be deserialized
	 * @param typeOfT
	 *            The specific genericized type of src. You can obtain this type
	 *            by using the {@link com.winterwell.gson.reflect.TypeToken} class.
	 *            For example, to get the type for {@code Collection<Foo>}, you
	 *            should use:
	 * 
	 *            <pre>
	 * Type typeOfT = new TypeToken&lt;Collection&lt;Foo&gt;&gt;() {
	 * }.getType();
	 * </pre>
	 * @return an object of type T from the json. Returns {@code null} if
	 *         {@code json} is {@code null}.
	 * @throws JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 *             typeOfT
	 * @since 1.3
	 */
	@SuppressWarnings("unchecked")
	public <T> T fromJson(JsonElement json, Type typeOfT)
			throws JsonSyntaxException {
		if (json == null) {
			return null;
		}
		return (T) fromJson(new JsonTreeReader(json), typeOfT);
	}

	static class FutureTypeAdapter<T> extends TypeAdapter<T> {
		private TypeAdapter<T> delegate;

		public void setDelegate(TypeAdapter<T> typeAdapter) {
			if (delegate != null) {
				throw new AssertionError();
			}
			delegate = typeAdapter;
		}

		@Override
		public T read(JsonReader in) throws IOException {
			if (delegate == null) {
				throw new IllegalStateException();
			}
			return delegate.read(in);
		}

		@Override
		public void write(JsonWriter out, T value) throws IOException {
			if (delegate == null) {
				throw new IllegalStateException();
			}
			delegate.write(out, value);
		}
	}

	@Override
	public String toString() {
		return new StringBuilder("{serializeNulls:").append(serializeNulls)
				.append(",classProperty:").append(classProperty)
				.append(",factories:").append(factories)
				.append(",instanceCreators:").append(constructorConstructor)
				.append("}").toString();
	}
	

	/**
	 * Strip out the _class properties added by this version of Gson.
	 * 
	 * @param json
	 * @return json without the classProperty bits
	 */
	public String removeClassProperty(String json) {
		if (classProperty == null)
			return json;
		String json2 = json.replaceAll("[\"']" + classProperty
				+ "[\"']:[\"'][^\"']*[\"'],?", "");
		return json2;
	}

	/**
	 * Use "@class" (see {@link #getClassProperty()}) if set to determine
	 * what class to make.
	 * @param json
	 * @return de-serialised POJO
	 * @since October 2014, added by Daniel
	 */
	public <T> T fromJson(String json) {
		if (classProperty==null) {
			throw new IllegalStateException("This method works via @class, which is switched off. Please use fromJson(String,Class)");
		}
		return (T) fromJson(json, Object.class);
	}
	
	/**
	 * Use "@class" (see {@link #getClassProperty()}) if set to determine
	 * what class to make.
	 * @param json
	 * @return de-serialised POJO
	 * @since October 2014, added by Daniel
	 */
	public <T> T fromJson(Reader json) {
		if (classProperty==null) {
			throw new IllegalStateException("This method works via @class, which is switched off. Please use fromJson(String,Class)");
		}
		return (T) fromJson(json, Object.class);
	}

	public <X> X convert(Map mapFromJson, Class<X> klass) {
		// inefficient, but should work
		String json = toJson(mapFromJson);
		X obj = fromJson(json, klass);
		return obj;
	}

	/**
	 * Uses no "@class" property
	 */
	private static Gson SAFE_GSON = GsonBuilder.safe().create();	
	
	
	/**
	 * This is the GSON used by {@link #toJSON(Object)} and {@link #fromJSON(String)}.
	 * @param gson
	 */
	public static void setDefaultGSON(Gson gson) {
		SAFE_GSON = gson;
	}
	
	/**
	 * Convenience for a safe robust default just-give-me-some-json convertor.
	 * @param obj
	 * @return json
	 */
	// Note: named with capitals to avoid conflict with fromJson()
	public static String toJSON(Object obj) {
		return SAFE_GSON.toJson(obj);
	}
	

	/**
	 * Convenience for a safe robust default just-read-me-some-json convertor.
	 * @param json
	 * @return object
	 */
	// Note: named with capitals to avoid conflict with toJson()
	public static Map fromJSON(String json) {
		return SAFE_GSON.fromJson(json, Map.class);
	}


	/**
	 * Can return null on error!
	 * @param _class
	 * @return
	 */
	public Class getClass(String _class) {
		// user defined type mapping?
		// See GsonBuilder.setClassMapping() 
		Class typeOfT = classForClass.get(_class);		
		if (typeOfT!=null) return typeOfT;
		try {
			typeOfT = Class.forName(_class);
			return typeOfT;
		} catch (ClassNotFoundException e) {
			return KErrorPolicy.process(classErrorPolicy, e);
		}		
	}
	
	public static class OneJsonDeserializationContext implements JsonDeserializationContext {
		public final Gson gson;

		public OneJsonDeserializationContext(Gson gson) {
			this.gson = gson;
		}

		@SuppressWarnings("unchecked")
		public <T> T deserialize(JsonElement json, Type typeOfT)
				throws JsonParseException 
		{
			return (T) gson.fromJson(json, typeOfT);
		}
	}
}



