/*
 * Copyright (C) 2011 Google Inc.
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

package com.winterwell.gson.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.winterwell.gson.InstanceCreator;
import com.winterwell.gson.JsonIOException;
import com.winterwell.gson.JsonParseException;
import com.winterwell.gson.reflect.TypeToken;
import com.winterwell.utils.Utils;

/**
 * Returns a function that can construct an instance of a requested type.
 */
public final class ConstructorConstructor {
	private final Map<Type, InstanceCreator<?>> instanceCreators;
	private String classProperty;  

	public ConstructorConstructor(Map<Type, InstanceCreator<?>> instanceCreators, String classProperty) {
		this.instanceCreators = instanceCreators;
		this.classProperty = classProperty;
	}

	public <T> ObjectConstructor<T> get(TypeToken<T> typeToken) {
		final Type type = typeToken.getType();
		final Class<? super T> rawType = typeToken.getRawType();

		// first try an instance creator

		@SuppressWarnings("unchecked") // types must agree
		final InstanceCreator<T> typeCreator = (InstanceCreator<T>) instanceCreators.get(type);
		if (typeCreator != null) {
			return new AObjectConstructor<T>(rawType) {
				public T construct() {
					return typeCreator.createInstance(type);
				}
			};
		}

		// Next try raw type match for instance creators
		@SuppressWarnings("unchecked") // types must agree
		final InstanceCreator<T> rawTypeCreator = (InstanceCreator<T>) instanceCreators.get(rawType);
		if (rawTypeCreator != null) {
			return new AObjectConstructor<T>(rawType) {
				public T construct() {
					return rawTypeCreator.createInstance(type);
				}
			};
		}

		ObjectConstructor<T> defaultConstructor = newDefaultConstructor(rawType);
		if (defaultConstructor != null) {
			return defaultConstructor;
		}

		ObjectConstructor<T> defaultImplementation = newDefaultImplementationConstructor(type, rawType);
		if (defaultImplementation != null) {
			return defaultImplementation;
		}

		// finally try unsafe
		return newUnsafeAllocator(type, rawType);
	}

	private <T> ObjectConstructor<T> newDefaultConstructor(Class<? super T> rawType) {
		try {
			return new DefaultObjectConstructor(rawType);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Constructors for common interface types like Map and List and their
	 * subytpes.
	 */
	@SuppressWarnings("unchecked") // use runtime checks to guarantee that 'T' is what it is
	private <T> ObjectConstructor<T> newDefaultImplementationConstructor(
			final Type type, Class<? super T> rawType) {
		if (Collection.class.isAssignableFrom(rawType)) {
			if (SortedSet.class.isAssignableFrom(rawType)) {
				return new AObjectConstructor<T>(rawType) {
					public T construct() {
						return (T) new TreeSet<Object>();
					}
				};
			} else if (EnumSet.class.isAssignableFrom(rawType)) {
				return new AObjectConstructor<T>(rawType) {
					@SuppressWarnings("rawtypes")
					public T construct() {
						if (type instanceof ParameterizedType) {
							Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
							if (elementType instanceof Class) {
								return (T) EnumSet.noneOf((Class)elementType);
							} else {
								throw new JsonIOException("Invalid EnumSet type: " + type.toString());
							}
						} else {
							throw new JsonIOException("Invalid EnumSet type: " + type.toString());
						}
					}
				};
			} else if (Set.class.isAssignableFrom(rawType)) {
				return new AObjectConstructor<T>(LinkedHashSet.class) {
					public T construct() {
						return (T) new LinkedHashSet<Object>();
					}
				};
			} else if (Queue.class.isAssignableFrom(rawType)) {
				return new AObjectConstructor<T>(LinkedList.class) {
					public T construct() {
						return (T) new LinkedList<Object>();
					}
				};
			} else {
				return new AObjectConstructor<T>(ArrayList.class) {
					public T construct() {
						return (T) new ArrayList<Object>();
					}
				};
			}
		}

		if (Map.class.isAssignableFrom(rawType)) {
			if (SortedMap.class.isAssignableFrom(rawType)) {
				return new AObjectConstructor<T>(TreeMap.class) {
					public T construct() {
						return (T) new TreeMap<Object, Object>();
					}
				};
			} else if (type instanceof ParameterizedType && !(String.class.isAssignableFrom(
					TypeToken.get(((ParameterizedType) type).getActualTypeArguments()[0]).getRawType()))) {
				return new AObjectConstructor<T>(LinkedHashMap.class) {
					public T construct() {
						return (T) new LinkedHashMap<Object, Object>();
					}
				};
			} else {
				return new AObjectConstructor<T>(LinkedTreeMap.class) {
					public T construct() {
						return (T) new LinkedTreeMap<String, Object>();
					}
				};
			}
		}

		return null;
	}

	private <T> ObjectConstructor<T> newUnsafeAllocator(
			final Type type, final Class<? super T> rawType) {
		return new AObjectConstructor<T>(rawType) {
			private final UnsafeAllocator unsafeAllocator = UnsafeAllocator.create();
			@SuppressWarnings("unchecked")
			public T construct() {
				try {
					Object newInstance = unsafeAllocator.newInstance(rawType);
					return (T) newInstance;
				} catch (Exception e) {
					throw new RuntimeException(("Unable to invoke no-args constructor for " + type + ". "
							+ "Register an InstanceCreator with Gson for this type may fix this problem."), e);
				}
			}
		};
	}

	@Override public String toString() {
		return instanceCreators.toString();
	}
}


abstract class AObjectConstructor<T> implements ObjectConstructor<T> {

	private Class type;

	/**
	 * @param type The type output by {@link #construct()} 
	 */
	AObjectConstructor(Class type) {
		this.type = type;
	}

	@Override
	public final String toString() {
		return "ObjectConstructor["+type+"]";
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public T construct(String string) {
		return ObjectConstructor.super.construct(string);
	}
}


class DefaultObjectConstructor<T> extends AObjectConstructor<T> {

	final Constructor<? super T> constructor;
	final Constructor<? super T> sconstructor;

	DefaultObjectConstructor(Class rawType) throws NoSuchMethodException, SecurityException {
		super(rawType);
		// no-args constructor
		Exception ex = null;
		Constructor<? super T> _constructor = null;
		try {
			_constructor = rawType.getDeclaredConstructor();
			if ( ! _constructor.isAccessible()) {
				_constructor.setAccessible(true);
			}	    
		} catch (NoSuchMethodException nosuch) {
			ex = nosuch;
		}	
		constructor = _constructor;
		
		// string constructor
		Constructor<? super T> scons = null;
		try {
			scons = rawType.getDeclaredConstructor(String.class);
			if ( ! scons.isAccessible()) {
				scons.setAccessible(true);
			}      
		} catch (NoSuchMethodException nosuch) {
			// oh well 	    	
		}	
		sconstructor = scons;
		if (constructor==null && sconstructor==null) {
			throw Utils.runtime(ex);
		}
	}

	@Override
	public T construct(String string) {
		if (sconstructor==null) {
			throw new JsonParseException("No String constructor for "+getType()+" input: "+string);
		}
		try {
			return (T) sconstructor.newInstance(string);
		} catch (InstantiationException e) {
			// TODO: JsonParseException ?
			throw new RuntimeException("Failed to invoke " + sconstructor + " with string "+string, e);
		} catch (InvocationTargetException e) {
			// TODO: don't wrap if cause is unchecked!
			// TODO: JsonParseException ?
			throw new RuntimeException("Failed to invoke " + sconstructor + " with string "+string, e);
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	@SuppressWarnings("unchecked") // T is the same raw type as is requested
	public T construct() {
		try {
			Object[] args = null;
			return (T) constructor.newInstance(args);
		} catch (InstantiationException e) {
			// TODO: JsonParseException ?
			throw new RuntimeException("Failed to invoke " + constructor + " with no args", e);
		} catch (InvocationTargetException e) {
			// TODO: don't wrap if cause is unchecked!
			// TODO: JsonParseException ?
			throw new RuntimeException("Failed to invoke " + constructor + " with no args",
					e.getTargetException());
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}


}
