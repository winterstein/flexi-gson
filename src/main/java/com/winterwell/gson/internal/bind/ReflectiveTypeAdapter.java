package com.winterwell.gson.internal.bind;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.winterwell.gson.Gson;
import com.winterwell.gson.JsonSyntaxException;
import com.winterwell.gson.KLoopPolicy;
import com.winterwell.gson.TypeAdapter;
import com.winterwell.gson.internal.ConstructorConstructor;
import com.winterwell.gson.internal.ObjectConstructor;
import com.winterwell.gson.internal.bind.ReflectiveTypeAdapterFactory.BoundField;
import com.winterwell.gson.reflect.TypeToken;
import com.winterwell.gson.stream.JsonReader;
import com.winterwell.gson.stream.JsonToken;
import com.winterwell.gson.stream.JsonWriter;
import com.winterwell.utils.MathUtils;
import com.winterwell.utils.ReflectionUtils;
import com.winterwell.utils.containers.Containers;

/**
 * Uses "@class" property to instantiate sub-classes.
 * @author daniel
 *
 * @param <T>
 */
final class ReflectiveTypeAdapter<T> extends TypeAdapter<T> {
	@Override
	public String toString() {
		return "ReflectiveTypeAdapter[type=" + type + "]";
	}

	private final Gson gson;
	private final String classProperty;
	private final ObjectConstructor<T> constructor;
	private final Map<String, BoundField> boundFields;
	private ConstructorConstructor conCon;
	private TypeToken<T> type;
	private ReflectiveTypeAdapterFactory factory;

	ReflectiveTypeAdapter(Gson gson, ReflectiveTypeAdapterFactory factory,
			TypeToken<T> type, ObjectConstructor<T> constructor,
			Map<String, BoundField> boundFields,
			ConstructorConstructor conCon, String classProperty) {
		this.gson = gson;
		this.factory = factory;
		this.type = type;
		this.constructor = constructor;
		this.boundFields = boundFields;
		this.conCon = conCon;
		this.classProperty = classProperty;
	}

	@Override
	public T read(JsonReader in) throws IOException {
		JsonToken peek = in.peek();
		if (peek == JsonToken.NULL) {
			in.nextNull();
			return null;
		}

		// So we're in the reflection based reader.
		// Should we replace the default constructor behaviour?
		// Check the reader for a _class property
		try {
			TypeAdapter<?> _typeAdapter = read2(in);
			if (_typeAdapter != null && _typeAdapter!=this) {
				return (T) _typeAdapter.read(in);
			}
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IOException(ex);
		}

		// Just a map
		if (constructor.getType() == Object.class) {
			return read2_justAMap(in, peek);
		}
		
		// special case - String constructors
		if (peek == JsonToken.STRING) {
			String s = in.nextString();
			T instance = constructor.construct(s);
			return instance;
		}
		
		try {
			// Make the Thing
			T instance = constructor.construct();
			// fill in its fields
			in.beginObject();
			while (in.hasNext()) {
				String name = in.nextName();
				BoundField field = boundFields.get(name);
				if (field == null || !field.deserialized) {
					in.skipValue();
				} else {
					// TODO LateBinding
					field.read(in, instance);
				}
			}
			in.endObject();
			return instance;
		} catch (IllegalStateException e) {
			throw new JsonSyntaxException(e);
		} catch(RuntimeException ex) {
			// An interface? We have to hope for a late find of a @class property
			if (constructor.getType().isInterface()) {			
				return read2_justAMap(in, peek);
			}
			throw ex;
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private T read2_justAMap(JsonReader in, JsonToken peek) throws IOException {
		if (peek==JsonToken.BEGIN_OBJECT) {
			// read the map in
			Map obj = read2_map(in);
			// JSOG id / ref?
			String id = null;
			if (gson.getLoopPolicy()==KLoopPolicy.JSOG) {
				String ref = (String) obj.get("@ref");
				if (ref!=null) {
					Object refVal = in.getIdValue(ref);
					if (refVal==null) {
						// we have to wait until the end to de-ref this
						return (T) new LateBinding(ref);
					}
					return (T) refVal;
				}
				id = (String) obj.remove("@id");	
			}
			try {
				// late find of the @class property? Or stay the same?
				T castObj = (T) read2_maybeChangeClass((Map<String, Object>) obj, in);
				// if we found an id, track the reference
				if (id!=null) {
					in.putIdValue(id, castObj);
				}
				return castObj;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
		
		Object obj = read3_value(in);
		return (T) obj;
	}

	/**
	 * Recurse (and recursively use our special sauce as well -- but avoid stack-overflow)
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private Map read2_map(JsonReader in) throws IOException {
    	Map<String,Object> temp = new HashMap();
    	in.beginObject();    	
    	while(in.hasNext()) {
    		String name = in.nextName();
    		// recursively read the value, inc our special sauce    		
    		Object value =read3_value(in);    		
    		temp.put(name, value);
    	}
    	in.endObject();
    	return temp;
	}

	/**
	 * Copy and paste code from {@link ObjectTypeAdapter} BUT with a true recursive call.
	 * Otherwise, our classes end up as primitives.
	 * 
	 * Note: We may have to further convert the outputs later, when we find out what they're meant to be. 
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private Object read3_value(JsonReader in) throws IOException {
		JsonToken token = in.peek();
		switch (token) {
		case BEGIN_ARRAY:
			List<Object> list = new ArrayList<Object>();
			in.beginArray();
			while (in.hasNext()) {
				list.add(read(in));
			}
			in.endArray();
			return list;

		case BEGIN_OBJECT:
			// recurse properly!
			Object map = gson.fromJson(in, Object.class);
			return map;

		case STRING:
			return in.nextString();

		case NUMBER:
			// Normally double, but can return BigInteger if needed
			return in.nextNumber();

		case BOOLEAN:
			return in.nextBoolean();

		case NULL:
			in.nextNull();
			return null;

		default:
			throw new IllegalStateException();
		}
	}

	Object read2_maybeChangeClass(Map<String, Object> map, JsonReader in) throws Exception {
		String _class = (String) map.get(classProperty);
		if (_class == null)
			return map;
		// get the Java class		
		Class typeOfT = gson.getClass(_class);		
		if (typeOfT==null) {
			return map; // class error
		}
		
		ObjectConstructor<?> con = conCon.get(TypeToken.get(typeOfT));
		Object obj = con.construct();
		// fill in the fields from map
		for (Map.Entry<String, Object> me : map.entrySet()) {
			final String fieldName = me.getKey();
			if (classProperty.equals(fieldName))
				continue;
			Class klass = obj.getClass(); // ??type.getRawType()
			Field f = getField(klass, fieldName);
			if (f == null) {
				if ("@id".equals(fieldName)) {
					in.putIdValue((String)me.getValue(), obj);
				}
				// a field has been removed from the class. Skip over it
				// Log.d("gson", "skip missing field "
				// + obj.getClass().getName() + " " + me);
				continue;
			}
			Object value = me.getValue();
			
			if (value == null) {
				// Set it anyway -- null may be replacing a non-null default
				f.set(obj, value);
				continue;
			}
			
			// LateBinding?
			assert value != null;
			value = read3_lateBinding(obj, f, -1, value, in);
			// ...cant resolve it yet?
			if (value==null) continue;
			
			// Class-correction: Correct for wrong-class choices, based on what the target field is.
			// Is the target a number? We get class-cast issues where gson
			// has opted for Double, but we need Integer
			value = read3_maybeChangeClass_changeFieldClass(in, f.getType(), value, fieldName);
			
			// Set it
			f.set(obj, value);
		}
		return obj;
	}

	/**
	 * 
	 * @param in
	 * @param fClass
	 * @param value
	 * @param fieldName
	 * @return possibly altered version of `value`
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private Object read3_maybeChangeClass_changeFieldClass(JsonReader in, final Class fClass, Object value, String fieldName)
			throws InstantiationException, IllegalAccessException 
	{		
		// Right class?
		if (ReflectionUtils.isa(value.getClass(), fClass)) {
			return value; // all fine
		}
		if (fClass != double.class && ReflectionUtils.isaNumber(fClass)) {
			return MathUtils.cast(fClass, (Number) value);			
		}
		if (fClass == byte.class || fClass== Byte.class) {
			if (value instanceof Number) {
				byte bv = ((Number)value).byteValue();
				return bv;
			}			
		}
		if (value instanceof String) {
			// If a Map convertor was used earlier, classes like Class and URI can end up as Strings
			// NB: we must re-wrap value as a json string to pass it back into Gson.
			String jsonValue = gson.toJson(value);
			value = gson.fromJson(jsonValue, fClass);
			return value;
			
		}
		// js style coercion number>string
		if (String.class.equals(fClass) && value instanceof Number) {
			return value.toString();
		}
		if (value instanceof Map) {
			// Handle Map/List sub-class choices based on the field
			// E.g. a ListMap could get serialised to {}, deserialised to LinkedHashMap -- in which case convert
			if (ReflectionUtils.isa(fClass, Map.class)) {
				Map mapSubClass = (Map) fClass.newInstance();
				mapSubClass.putAll((Map)value);
				return mapSubClass;
			}
			// Bug seen August 2017 with GL adserver MonetaryAmount
			// ?How did we get here?! This should have been converted to a POJO
			String valueJson = Gson.toJSON(value);
			Object value2 = gson.fromJson(valueJson, fClass);
			return value2;			
		}
		if (value instanceof Collection) {
			// Same as Map above, but for Lists, Sets and Arrays
			Collection collection = (Collection)value;
			// an array?
			if (fClass.isArray()) {
				final Class compType = fClass.getComponentType(); // not null 'cos array
				Object array = Array.newInstance(compType, collection.size());
				int i=0;
				for(Object vi : collection) {
					if (vi!=null) {
						vi = read3_lateBinding(array, null, i, vi, in);
						vi = read3_maybeChangeClass_changeFieldClass(in, compType, vi, fieldName);
					}					
					Array.set(array, i, vi);
					i++;
				}
				return array;				
			}
			if ( ! ReflectionUtils.isa(fClass, Collection.class)) {
				// unwrap a solo element 
				if (collection.size() == 1) {
					Object c0 = Containers.first(collection);
					if (c0==null) {
						return null;
					}
					// Right class or can it be converted? (with a guard against loops)
					Object converted0 = read3_maybeChangeClass_changeFieldClass(in, fClass, c0, fieldName);
					return converted0;
				}
				// what to do??
				throw new ClassCastException(fieldName+" Expected a Collection, got: "+fClass+" value: "+value);
			}
			Collection listSubClass = (Collection) fClass.newInstance();
			int i=0;
			for(Object vi : collection) {
				if (vi!=null) {
					vi = read3_lateBinding(listSubClass, null, i, vi, in);
					// NB: erasure means we have no component type info here
				}
				listSubClass.add(vi);
				i++;
			}
			return listSubClass;			
		} // end of class correction
		return value;
	}

	/**
	 * 
	 * @param value
	 * @param in 
	 * @param value2 
	 * @param f 
	 * @return value/lookup-of-value, or null if value was a LateBinding we cant yet resolve.
	 */
	private Object read3_lateBinding(Object obj, Field f, int index, Object value, JsonReader in) {
		assert value != null;
		if ( ! (value instanceof LateBinding)) return value;
		LateBinding lb = (LateBinding) value;
		Object val = in.getIdValue(lb.ref);
		if (val==null) {
			in.addLateBinding(obj, f, index, lb);
			return null;
		}
		return val;	
	}

	private Field getField(Class klass, String key) {
		// NB: often klass != type.getRawType(), but a sub-class

		// ?? slightly inefficient -- we look up the Fields each time,
		// whereas elsewhere we cache them.
		Field f = ReflectionUtils.getField(klass, key);
		if (f==null) return null;
		f.setAccessible(true);
		return f;
	}

	/**
	 * Check the reader for a "@class" property
	 * 
	 * @param in
	 * @return the TypeAdaptor to use, or null to carry on with the default
	 * @throws Exception
	 */
	private TypeAdapter<?> read2(JsonReader in) throws Exception {
		if (classProperty == null)
			return null;
		JsonReader _reader = in.getShortTermCopy();
		try {
			JsonToken peeked = _reader.peek();
			if (peeked != JsonToken.BEGIN_OBJECT) {
				return null;
			}
			_reader.beginObject();
			if (!_reader.hasNext()) {
				return null;
			}
			String name = _reader.nextName();
			if ( ! classProperty.equals(name)) {
				return null;
			}
			if (_reader.peek()==JsonToken.NULL) {
				_reader.nextNull();
				return null; // key: null
			}
			String klass = _reader.nextString();
			return read3(klass);
		} finally {
			in.reset();
		}
	}

	/**
	 * @param klass
	 * @return
	 * @throws ClassNotFoundException
	 */
	TypeAdapter read3(String klass) throws ClassNotFoundException {
		if (klass==null) {
			return null; // no info (as you were -- use constructor -- cos what else can you do)
		}
		// what does the constructor handle??
		if (constructor.getType().getCanonicalName().equals(klass)) {
			// no change needed (as you were -- use constructor)
			return null;
		}		
		Class typeOfT = gson.getClass(klass);
		TypeToken tt = TypeToken.get(typeOfT);
		TypeAdapter _typeAdapter = gson.getAdapter(tt);
		return _typeAdapter;
	}

	@Override
	public void write(JsonWriter out, T value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}
		
		// Loop check
		boolean ok = out.beginLoopCheck(gson.getLoopPolicy(), value);
		if (!ok) {
			out.nullValue();
			return;
		}

		out.beginObject();		
		
		// id / ref?
		if (gson.getLoopPolicy()==KLoopPolicy.JSOG) {
			String ref = out.getRef(value);
			if (ref != null) {
				// already seen -- just reference it
				out.name("@ref");
				out.value(ref);
				out.endObject();
				if (gson.getLoopPolicy() != KLoopPolicy.NO_CHECKS) {
					out.endLoopCheck(value);
				}
				return;
			}
		}
		// id? TODO do after the class-property, so that class can be found with a peek.
		// Which would be more efficient. Except, that code isn't ready yet.
		if (gson.getLoopPolicy()==KLoopPolicy.JSOG) {
			String id = out.getNewId(value);
			out.name("@id");
			out.value(id);
		}

		if (classProperty != null) {
			// anonymous classes return null here
			String cName = value.getClass().getCanonicalName();
			// We can't do anything sensible at de-serialisation with
			// member-classes.
			// So leave it for the object->map adapter
			boolean mc = value.getClass().isMemberClass();
			if (mc || cName==null) {
				// ??
			} else if (value.getClass() != Object.class) {
				out.name(classProperty);
				out.value(cName);
			}
		}
		
		try {
			// Maps should not be handled here
			assert boundFields!=null || ReflectionUtils.isa(value.getClass(), Map.class) : value.getClass();
			
			for (BoundField boundField : boundFields.values()) {
				if (boundField.serialized) {
					out.name(boundField.name);
					boundField.write(out, value);
				}
			}
		} catch (IllegalAccessException e) {
			throw new AssertionError();
		}
		// close
		out.endObject();
		if (gson.getLoopPolicy() != KLoopPolicy.NO_CHECKS) {
			out.endLoopCheck(value);
		}
	}
}