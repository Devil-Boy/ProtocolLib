/*
 *  ProtocolLib - Bukkit server library that allows access to the Minecraft protocol.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */

package com.comphenix.protocol.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.util.com.google.common.base.Joiner;
import net.minecraft.util.com.google.common.collect.Sets;

import com.comphenix.protocol.reflect.fuzzy.AbstractFuzzyMatcher;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Retrieves fields and methods by signature, not just name.
 * 
 * @author Kristian
 */
public class FuzzyReflection {
	/**
	 * Represents an interface for accessing a field.
	 * @author Kristian
	 */
	public interface FieldAccessor {
		/**
		 * Retrieve the value of a field for a particular instance.
		 * @param instance - the instance, or NULL for a static field.
		 * @return The value of the field.
		 * @throws IllegalStateException If the current security context prohibits reflection.
		 */
		public Object get(Object instance);
		
		/**
		 * Set the value of a field for a particular instance.
		 * @param instance - the instance, or NULL for a static field.
		 * @param value - the new value of the field.
		 */
		public void set(Object instance, Object value);
	}
	
	/**
	 * Represents an interface for invoking a method.
	 * @author Kristian 
	 */
	public interface MethodAccessor {
		/**
		 * Invoke the underlying method.
		 * @param target - the target instance, or NULL for a static method.
		 * @param args - the arguments to pass to the method.
		 * @return The return value, or NULL for void methods.
		 */
		public Object invoke(Object target, Object... args);
	}
	
	// The class we're actually representing
	private Class<?> source;

	// Whether or not to lookup private members
	private boolean forceAccess;

	public FuzzyReflection(Class<?> source, boolean forceAccess) {
		this.source = source;
		this.forceAccess = forceAccess;
	}
	
	/**
	 * Retrieves a fuzzy reflection instance from a given class.
	 * @param source - the class we'll use.
	 * @return A fuzzy reflection instance.
	 */
	public static FuzzyReflection fromClass(Class<?> source) {
		return fromClass(source, false);
	}
	
	/**
	 * Retrieves a fuzzy reflection instance from a given class.
	 * @param source - the class we'll use.
	 * @param forceAccess - whether or not to override scope restrictions.
	 * @return A fuzzy reflection instance.
	 */
	public static FuzzyReflection fromClass(Class<?> source, boolean forceAccess) {
		return new FuzzyReflection(source, forceAccess);
	}
	
	/**
	 * Retrieves a fuzzy reflection instance from an object.
	 * @param reference - the object we'll use.
	 * @return A fuzzy reflection instance that uses the class of the given object.
	 */
	public static FuzzyReflection fromObject(Object reference) {
		return new FuzzyReflection(reference.getClass(), false);
	}
	
	/**
	 * Retrieves a fuzzy reflection instance from an object.
	 * @param reference - the object we'll use.
	 * @param forceAccess - whether or not to override scope restrictions.
	 * @return A fuzzy reflection instance that uses the class of the given object.
	 */
	public static FuzzyReflection fromObject(Object reference, boolean forceAccess) {
		return new FuzzyReflection(reference.getClass(), forceAccess);
	}
	
	/**
	 * Retrieve an accessor for the first field of the given type.
	 * @param instanceClass - the type of the instance to retrieve.
	 * @param fieldClass - type of the field to retrieve.
	 * @param forceAccess - whether or not to look for private and protected fields.
	 * @return The value of that field.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	public static FieldAccessor getFieldAccessor(Class<?> instanceClass, Class<?> fieldClass, boolean forceAccess) {	
		// Get a field accessor
		Field field = FuzzyReflection.fromClass(instanceClass, forceAccess).getFieldByType(null, fieldClass);
		return getFieldAccessor(field);
	}
	
	/**
	 * Retrieve an accessor for the first field of the given type.
	 * @param instanceClass - the type of the instance to retrieve.
	 * @param fieldClass - type of the field to retrieve.
	 * @param forceAccess - whether or not to look for private and protected fields.
	 * @return The value of that field.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	public static FieldAccessor getFieldAccessor(Class<?> instanceClass, String fieldName, boolean forceAccess) {	
		return getFieldAccessor(FieldUtils.getField(instanceClass, fieldName, forceAccess));
	}
	
	/**
	 * Retrieve a field accessor from a given field that uses unchecked exceptions.
	 * @param field - the field.
	 * @return The field accessor.
	 */
	public static FieldAccessor getFieldAccessor(final Field field) {
		return getFieldAccessor(field, true);
	}
	
	/**
	 * Retrieve a field accessor from a given field that uses unchecked exceptions.
	 * @param field - the field.
	 * @param forceAccess - whether or not to skip Java access checking.
	 * @return The field accessor.
	 */
	public static FieldAccessor getFieldAccessor(final Field field, boolean forceAccess) {
		field.setAccessible(true);
		
		return new FieldAccessor() {
			@Override
			public Object get(Object instance) {
				try {
					return field.get(instance);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Cannot use reflection.", e);
				}
			}
			
			@Override
			public void set(Object instance, Object value) {
				try {
					field.set(instance, value);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Cannot use reflection.", e);
				}
			}
		};
	}
	
	/**
	 * Retrieve a method accessor for a method with the given name and signature.
	 * @param instanceClass - the parent class.
	 * @param name - the method name.
	 * @param parameters - the parameters.
	 * @return The method accessor.
	 */
	public static MethodAccessor getMethodAccessor(Class<?> instanceClass, String name, Class<?>... parameters) {
		return getMethodAccessor(instanceClass, instanceClass, name, parameters);
	}
	
	// Helper method
	private static MethodAccessor getMethodAccessor(
		Class<?> initialClass, Class<?> instanceClass, String name, Class<?>... parameters) {
		
		try {
			Method method = instanceClass.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return getMethodAccessor(method);
			
		} catch (NoSuchMethodException e) {
			// Search for a private method in the superclass
			if (initialClass.getSuperclass() != null)
				return getMethodAccessor(initialClass, instanceClass.getSuperclass(), name, parameters);
			
			// Unable to find it
			throw new IllegalArgumentException("Unable to find method " + name +
					"(" + Joiner.on(", ").join(parameters) +") in " + initialClass);
			
		} catch (Exception e) {
			throw new RuntimeException("Unable to retrieve methods.", e);
		}
	}
	
	/**
	 * Retrieve a method accessor for a particular method, avoding checked exceptions.
	 * @param method - the method to access.
	 * @return The method accessor.
	 */
	public static MethodAccessor getMethodAccessor(final Method method) {
		return new MethodAccessor() {
			@Override
			public Object invoke(Object target, Object... args) {
				try {
					return method.invoke(target, args);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Cannot use reflection.", e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException("An internal error occured.", e.getCause());
				} catch (IllegalArgumentException e) {
					throw e;
				}
			}
		};
	}
	
	/**
	 * Retrieve the value of the first field of the given type.
	 * @param instance - the instance to retrieve from.
	 * @param fieldClass - type of the field to retrieve.
	 * @param forceAccess - whether or not to look for private and protected fields.
	 * @return The value of that field.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	public static <T> T getFieldValue(Object instance, Class<T> fieldClass, boolean forceAccess) {
		@SuppressWarnings("unchecked")
		T result = (T) getFieldAccessor(instance.getClass(), fieldClass, forceAccess).get(instance);
		return result;
	}
	
	/**
	 * Retrieves the underlying class.
	 */
	public Class<?> getSource() {
		return source;
	}
		
	/**
	 * Retrieve the singleton instance of a class, from a method or field.
	 * @return The singleton instance.
	 * @throws IllegalStateException If the class has no singleton.
	 */
	public Object getSingleton() {	
		Method method = null;
		Field field = null;
		
		try {
			method = getMethod(
				FuzzyMethodContract.newBuilder().
					parameterCount(0).
					returnDerivedOf(source).
					requireModifier(Modifier.STATIC).
					build()
			);
		} catch (IllegalArgumentException e) {
			// Try getting the field instead
			// Note that this will throw an exception if not found
			field = getFieldByType("instance", source.getClass());
		}

		// Convert into unchecked exceptions
		if (method != null) {
			try {
				method.setAccessible(true);
				return method.invoke(null);
			} catch (Exception e) {
				throw new RuntimeException("Cannot invoke singleton method " + method, e);
			}
		}
		if (field != null) {
			try {
				field.setAccessible(true);
				return field.get(null);
			} catch (Exception e) {
				throw new IllegalArgumentException("Cannot get content of singleton field " + field, e);
			}
		}
		// We should never get to this point
		throw new IllegalStateException("Impossible.");
	}
	
	/**
	 * Retrieve the first method that matches.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level method.
	 * @param matcher - the matcher to use.
	 * @return The first method that satisfies the given matcher.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Method getMethod(AbstractFuzzyMatcher<MethodInfo> matcher) {
		List<Method> result = getMethodList(matcher);
		
		if (result.size() > 0)
			return result.get(0);
		else
			throw new IllegalArgumentException("Unable to find a method that matches " + matcher);
	}
	
	/**
	 * Retrieve a list of every method that matches the given matcher.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level methods.
	 * @param matcher - the matcher to apply.
	 * @return List of found methods.
	 */
	public List<Method> getMethodList(AbstractFuzzyMatcher<MethodInfo> matcher) {
		List<Method> methods = Lists.newArrayList();
		
		// Add all matching fields to the list
		for (Method method : getMethods()) {
			if (matcher.isMatch(MethodInfo.fromMethod(method), source)) {
				methods.add(method);
			}
		}
		return methods;
	}
	
	/**
	 * Retrieves a method by looking at its name.
	 * @param nameRegex -  regular expression that will match method names.
	 * @return The first method that satisfies the regular expression.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Method getMethodByName(String nameRegex) {
		Pattern match = Pattern.compile(nameRegex);
		
		for (Method method : getMethods()) {
			if (match.matcher(method.getName()).matches()) {
				// Right - this is probably it. 
				return method;
			}
		}

		throw new IllegalArgumentException("Unable to find a method with the pattern " + 
									nameRegex + " in " + source.getName());
	}
	
	/**
	 * Retrieves a method by looking at the parameter types only.
	 * @param name - potential name of the method. Only used by the error mechanism.
	 * @param args - parameter types of the method to find.
	 * @return The first method that satisfies the parameter types.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Method getMethodByParameters(String name, Class<?>... args) {
		// Find the correct method to call
		for (Method method : getMethods()) {
			if (Arrays.equals(method.getParameterTypes(), args)) {
				return method;
			}
		}
		
		// That sucks
		throw new IllegalArgumentException("Unable to find " + name + " in " + source.getName());
	}
	
	/**
	 * Retrieves a method by looking at the parameter types and return type only.
	 * @param name - potential name of the method. Only used by the error mechanism.
	 * @param returnType - return type of the method to find.
	 * @param args - parameter types of the method to find.
	 * @return The first method that satisfies the parameter types.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Method getMethodByParameters(String name, Class<?> returnType, Class<?>[] args) {
		// Find the correct method to call
		List<Method> methods = getMethodListByParameters(returnType, args);
		
		if (methods.size() > 0) {
			return methods.get(0);
		} else {
			// That sucks
			throw new IllegalArgumentException("Unable to find " + name + " in " + source.getName());
		}
	}
	
	/**
	 * Retrieves a method by looking at the parameter types and return type only.
	 * @param name - potential name of the method. Only used by the error mechanism.
	 * @param returnTypeRegex - regular expression matching the return type of the method to find.
	 * @param argsRegex - regular expressions of the matching parameter types.
	 * @return The first method that satisfies the parameter types.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Method getMethodByParameters(String name, String returnTypeRegex, String[] argsRegex) {
		Pattern match = Pattern.compile(returnTypeRegex);
		Pattern[] argMatch = new Pattern[argsRegex.length];
		
		for (int i = 0; i < argsRegex.length; i++) {
			argMatch[i] = Pattern.compile(argsRegex[i]);
		}
		
		// Find the correct method to call
		for (Method method : getMethods()) {
			if (match.matcher(method.getReturnType().getName()).matches()) {
				if (matchParameters(argMatch, method.getParameterTypes()))
					return method;
			}
		}
		
		// That sucks
		throw new IllegalArgumentException("Unable to find " + name + " in " + source.getName());
	}
	
	private boolean matchParameters(Pattern[] parameterMatchers, Class<?>[] argTypes) {
		if (parameterMatchers.length != argTypes.length)
			throw new IllegalArgumentException("Arrays must have the same cardinality.");
		
		// Check types against the regular expressions
		for (int i = 0; i < argTypes.length; i++) {
			if (!parameterMatchers[i].matcher(argTypes[i].getName()).matches())
				return false;
		}
		
		return true;
	}
	
	/**
	 * Retrieves every method that has the given parameter types and return type.
	 * @param returnType - return type of the method to find.
	 * @param args - parameter types of the method to find.
	 * @return Every method that satisfies the given constraints.
	 */
	public List<Method> getMethodListByParameters(Class<?> returnType, Class<?>[] args) {
		List<Method> methods = new ArrayList<Method>();
		
		// Find the correct method to call
		for (Method method : getMethods()) {
			if (method.getReturnType().equals(returnType) && Arrays.equals(method.getParameterTypes(), args)) {
				methods.add(method);
			}
		}
		return methods;
	}
	
	/**
	 * Retrieves a field by name.
	 * @param nameRegex - regular expression that will match a field name.
	 * @return The first field to match the given expression.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	public Field getFieldByName(String nameRegex) {
		Pattern match = Pattern.compile(nameRegex);
		
		for (Field field : getFields()) {
			if (match.matcher(field.getName()).matches()) {
				// Right - this is probably it. 
				return field;
			}
		}
		
		// Looks like we're outdated. Too bad.
		throw new IllegalArgumentException("Unable to find a field with the pattern " + 
									nameRegex + " in " + source.getName());
	}
	
	/**
	 * Retrieves the first field with a type equal to or more specific to the given type.
	 * @param name - name the field probably is given. This will only be used in the error message.
	 * @param type - type of the field to find.
	 * @return The first field with a type that is an instance of the given type.
	 */
	public Field getFieldByType(String name, Class<?> type) {
		List<Field> fields = getFieldListByType(type);
		
		if (fields.size() > 0) {
			return fields.get(0);
		} else {
			// Looks like we're outdated. Too bad.
			throw new IllegalArgumentException(String.format("Unable to find a field %s with the type %s in %s",
					name, type.getName(), source.getName())
			);
		}
	}
	
	/**
	 * Retrieves every field with a type equal to or more specific to the given type.
	 * @param type - type of the fields to find.
	 * @return Every field with a type that is an instance of the given type.
	 */
	public List<Field> getFieldListByType(Class<?> type) {
		List<Field> fields = new ArrayList<Field>();
		
		// Field with a compatible type
		for (Field field : getFields()) {
			// A assignable from B -> B instanceOf A
			if (type.isAssignableFrom(field.getType())) {
				fields.add(field);
			}
		}
		
		return fields;
	}
	
	/**
	 * Retrieve the first field that matches.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level fields.
	 * @param matcher - the matcher to use.
	 * @return The first method that satisfies the given matcher.
	 * @throws IllegalArgumentException If the method cannot be found.
	 */
	public Field getField(AbstractFuzzyMatcher<Field> matcher) {
		List<Field> result = getFieldList(matcher);
		
		if (result.size() > 0)
			return result.get(0);
		else
			throw new IllegalArgumentException("Unable to find a field that matches " + matcher);
	}
	
	/**
	 * Retrieve a list of every field that matches the given matcher.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level fields.
	 * @param matcher - the matcher to apply.
	 * @return List of found fields.
	 */
	public List<Field> getFieldList(AbstractFuzzyMatcher<Field> matcher) {
		List<Field> fields = Lists.newArrayList();
		
		// Add all matching fields to the list
		for (Field field : getFields()) {
			if (matcher.isMatch(field, source)) {
				fields.add(field);
			}
		}
		return fields;
	}
	
	/**
	 * Retrieves a field by type.
	 * <p>
	 * Note that the type is matched using the full canonical representation, i.e.: 
	 * <ul>
	 *     <li>java.util.List</li>
	 *     <li>net.comphenix.xp.ExperienceMod</li>
	 * </ul>
	 * @param typeRegex - regular expression that will match the field type.
	 * @return The first field with a type that matches the given regular expression.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	public Field getFieldByType(String typeRegex) {
		
		Pattern match = Pattern.compile(typeRegex);
		
		// Like above, only here we test the field type
		for (Field field : getFields()) {
			String name = field.getType().getName();
			
			if (match.matcher(name).matches()) {
				return field;
			}
		}
		
		// Looks like we're outdated. Too bad.
		throw new IllegalArgumentException("Unable to find a field with the type " + 
										   typeRegex + " in " + source.getName());
	}
	
	/**
	 * Retrieves a field by type.
	 * <p>
	 * Note that the type is matched using the full canonical representation, i.e.: 
	 * <ul>
	 *     <li>java.util.List</li>
	 *     <li>net.comphenix.xp.ExperienceMod</li>
	 * </ul>
	 * @param typeRegex - regular expression that will match the field type.
	 * @param ignored - types to ignore.
	 * @return The first field with a type that matches the given regular expression.
	 * @throws IllegalArgumentException If the field cannot be found.
	 */
	@SuppressWarnings("rawtypes")
	public Field getFieldByType(String typeRegex, Set<Class> ignored) {
		
		Pattern match = Pattern.compile(typeRegex);
		
		// Like above, only here we test the field type
		for (Field field : getFields()) {
			Class type = field.getType();
			
			if (!ignored.contains(type) && match.matcher(type.getName()).matches()) {
				return field;
			}
		}
		
		// Looks like we're outdated. Too bad.
		throw new IllegalArgumentException("Unable to find a field with the type " + 
									       typeRegex + " in " + source.getName());
	}
	
	/**
	 * Retrieve the first constructor that matches.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level constructors.
	 * @param matcher - the matcher to use.
	 * @return The first constructor that satisfies the given matcher.
	 * @throws IllegalArgumentException If the constructor cannot be found.
	 */
	public Constructor<?> getConstructor(AbstractFuzzyMatcher<MethodInfo> matcher) {
		List<Constructor<?>> result = getConstructorList(matcher);
		
		if (result.size() > 0)
			return result.get(0);
		else
			throw new IllegalArgumentException("Unable to find a method that matches " + matcher);
	}
	
	/**
	 * Retrieve every method as a map over names. 
	 * <p>
	 * Note that overloaded methods will only occur once in the resulting map.
	 * @param methods - every method.
	 * @return A map over every given method.
	 */
	public Map<String, Method> getMappedMethods(List<Method> methods) {
		Map<String, Method> map = Maps.newHashMap();
		
		for (Method method : methods) {
			map.put(method.getName(), method);
		}
		return map;
	}
	
	/**
	 * Retrieve a list of every constructor that matches the given matcher.
	 * <p>
	 * ForceAccess must be TRUE in order for this method to access private, protected and package level constructors.
	 * @param matcher - the matcher to apply.
	 * @return List of found constructors.
	 */
	public List<Constructor<?>> getConstructorList(AbstractFuzzyMatcher<MethodInfo> matcher) {
		List<Constructor<?>> constructors = Lists.newArrayList();
		
		// Add all matching fields to the list
		for (Constructor<?> constructor : getConstructors()) {
			if (matcher.isMatch(MethodInfo.fromConstructor(constructor), source)) {
				constructors.add(constructor);
			}
		}
		return constructors;
	}
	
	/**
	 * Retrieves all private and public fields in declared order (after JDK 1.5).
	 * <p>
	 * Private, protected and package fields are ignored if forceAccess is FALSE.
	 * @return Every field.
	 */
	public Set<Field> getFields() {
		// We will only consider private fields in the declared class
		if (forceAccess)
			return setUnion(source.getDeclaredFields(), source.getFields());
		else
			return setUnion(source.getFields());
	}
	
	/**
	 * Retrieves all private and public fields, up until a certain superclass.
	 * @param excludeClass - the class (and its superclasses) to exclude from the search.
	 * @return Every such declared field.
	 */
	public Set<Field> getDeclaredFields(Class<?> excludeClass) {
		if (forceAccess) {
			Class<?> current = source;
			Set<Field> fields = Sets.newLinkedHashSet();
			
			while (current != null && current != excludeClass) {
				fields.addAll(Arrays.asList(current.getDeclaredFields()));
				current = current.getSuperclass();
			}
			return fields;
		}
		return getFields();
	}
	
	/**
	 * Retrieves all private and public methods in declared order (after JDK 1.5).
	 * <p>
	 * Private, protected and package methods are ignored if forceAccess is FALSE.
	 * @return Every method.
	 */
	public Set<Method> getMethods() {
		// We will only consider private methods in the declared class
		if (forceAccess)
			return setUnion(source.getDeclaredMethods(), source.getMethods());
		else
			return setUnion(source.getMethods());
	}
	
	/**
	 * Retrieves all private and public constructors in declared order (after JDK 1.5).
	 * <p>
	 * Private, protected and package constructors are ignored if forceAccess is FALSE.
	 * @return Every constructor.
	 */
	public Set<Constructor<?>> getConstructors() {
		if (forceAccess)
			return setUnion(source.getDeclaredConstructors());
		else
			return setUnion(source.getConstructors());
	}
	
	// Prevent duplicate fields
	private static <T> Set<T> setUnion(T[]... array) {
		Set<T> result = new LinkedHashSet<T>();
		
		for (T[] elements : array) {
			for (T element : elements) {
				result.add(element);
			}
		}
		return result;
	}
	
	/**
	 * Retrieves whether or not not to override any scope restrictions.
	 * @return TRUE if we override scope, FALSE otherwise.
	 */
	public boolean isForceAccess() {
		return forceAccess;
	}

	/**
	 * Sets whether or not not to override any scope restrictions.
	 * @param forceAccess - TRUE if we override scope, FALSE otherwise.
	 */
	public void setForceAccess(boolean forceAccess) {
		this.forceAccess = forceAccess;
	}
}
