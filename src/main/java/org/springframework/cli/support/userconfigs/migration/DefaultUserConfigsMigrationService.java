/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cli.support.userconfigs.migration;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.cli.support.userconfigs.migration.GenericUserConfigsMigrator.MigratablePair;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Default implementation of a {@link UserConfigsMigrationService}.
 *
 * @author Janne Valkealahti
 */
public class DefaultUserConfigsMigrationService implements UserConfigsMigrationService, UserConfigsMigratorRegistry {

	private final Migrators migrators = new Migrators();
	private final Map<ConverterCacheKey, GenericUserConfigsMigrator> converterCache = new ConcurrentReferenceHashMap<>(64);

	/**
	 * General NO-OP converter used when conversion is not required.
	 */
	private static final GenericUserConfigsMigrator NO_OP_CONVERTER = new NoOpMigrator("NO_OP");

	/**
	 * Used as a cache entry when no converter is available.
	 * This converter is never returned.
	 */
	private static final GenericUserConfigsMigrator NO_MATCH = new NoOpMigrator("NO_MATCH");

	@Override
	public void addMigrator(UserConfigsMigrator<?, ?> migrator) {
		ResolvableType[] typeInfo = getRequiredTypeInfo(migrator.getClass(), UserConfigsMigrator.class);
		if (typeInfo == null && migrator instanceof DecoratingProxy decoratingProxy) {
			typeInfo = getRequiredTypeInfo(decoratingProxy.getDecoratedClass(), UserConfigsMigrator.class);
		}
		if (typeInfo == null) {
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your " +
					"Converter [" + migrator.getClass().getName() + "]; does the class parameterize those types?");
		}
		addMigrator(new ConverterAdapter(migrator, typeInfo[0], typeInfo[1]));
	}

	@Override
	public <S, T> void addMigrator(Class<S> sourceType, Class<T> targetType, UserConfigsMigrator<? super S, ? extends T> migrator) {
		addMigrator(new ConverterAdapter(
				migrator, ResolvableType.forClass(sourceType), ResolvableType.forClass(targetType)));
	}

	@Override
	public void addMigrator(GenericUserConfigsMigrator migrator) {
		this.migrators.add(migrator);
		// invalidateCache();
	}

	@Nullable
	private ResolvableType[] getRequiredTypeInfo(Class<?> converterClass, Class<?> genericIfc) {
		ResolvableType resolvableType = ResolvableType.forClass(converterClass).as(genericIfc);
		ResolvableType[] generics = resolvableType.getGenerics();
		if (generics.length < 2) {
			return null;
		}
		Class<?> sourceType = generics[0].resolve();
		Class<?> targetType = generics[1].resolve();
		if (sourceType == null || targetType == null) {
			return null;
		}
		return generics;
	}

	@Override
	public boolean canMigrate(Class<?> sourceType, Class<?> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return canConvert((sourceType != null ? TypeDescriptor.valueOf(sourceType) : null),
				TypeDescriptor.valueOf(targetType));
	}

	public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			return true;
		}
		GenericUserConfigsMigrator converter = getConverter(sourceType, targetType);
		return (converter != null);
	}

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T migrate(Object source, Class<T> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return (T) convert(source, TypeDescriptor.forObject(source), TypeDescriptor.valueOf(targetType));
	}


	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convert(@Nullable Object source, Class<T> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return (T) convert(source, TypeDescriptor.forObject(source), TypeDescriptor.valueOf(targetType));
	}

	@Nullable
	public Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			Assert.isTrue(source == null, "Source must be [null] if source type == [null]");
			return handleResult(null, targetType, convertNullSource(null, targetType));
		}
		if (source != null && !sourceType.getObjectType().isInstance(source)) {
			throw new IllegalArgumentException("Source to convert from must be an instance of [" +
					sourceType + "]; instead it was a [" + source.getClass().getName() + "]");
		}
		GenericUserConfigsMigrator converter = getConverter(sourceType, targetType);
		if (converter != null) {
			Object result = invokeConverter(converter, source, sourceType, targetType);
			return handleResult(sourceType, targetType, result);
		}
		return handleConverterNotFound(source, sourceType, targetType);
	}

	@Nullable
	public static Object invokeConverter(GenericUserConfigsMigrator converter, @Nullable Object source,
			TypeDescriptor sourceType, TypeDescriptor targetType) {

		try {
			return converter.convert(source, sourceType, targetType);
		}
		catch (UserConfigsMigrationFailedException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new UserConfigsMigrationFailedException(sourceType, targetType, source, ex);
		}
	}

	@Nullable
	private Object handleConverterNotFound(
			@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {

		if (source == null) {
			assertNotPrimitiveTargetType(sourceType, targetType);
			return null;
		}
		if ((sourceType == null || sourceType.isAssignableTo(targetType)) &&
				targetType.getObjectType().isInstance(source)) {
			return source;
		}
		throw new UserConfigsMigratorNotFoundException(sourceType, targetType);
	}

	@Nullable
	private Object handleResult(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType, @Nullable Object result) {
		if (result == null) {
			assertNotPrimitiveTargetType(sourceType, targetType);
		}
		return result;
	}

	private void assertNotPrimitiveTargetType(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.isPrimitive()) {
			throw new UserConfigsMigrationFailedException(sourceType, targetType, null,
					new IllegalArgumentException("A null value cannot be assigned to a primitive type"));
		}
	}

	/**
	 * Template method to convert a {@code null} source.
	 * <p>The default implementation returns {@code null} or the Java 8
	 * {@link java.util.Optional#empty()} instance if the target type is
	 * {@code java.util.Optional}. Subclasses may override this to return
	 * custom {@code null} objects for specific target types.
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the converted null object
	 */
	@Nullable
	protected Object convertNullSource(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.getObjectType() == Optional.class) {
			return Optional.empty();
		}
		return null;
	}

	/**
	 * Convenience operation for converting a source object to the specified targetType,
	 * where the target type is a descriptor that provides additional conversion context.
	 * Simply delegates to {@link #convert(Object, TypeDescriptor, TypeDescriptor)} and
	 * encapsulates the construction of the source type descriptor using
	 * {@link TypeDescriptor#forObject(Object)}.
	 * @param source the source object
	 * @param targetType the target type
	 * @return the converted value
	 * @throws UserConfigsMigrationException if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null},
	 * or sourceType is {@code null} but source is not {@code null}
	 */
	@Nullable
	public Object convert(@Nullable Object source, TypeDescriptor targetType) {
		return convert(source, TypeDescriptor.forObject(source), targetType);
	}



	/**
	 * Hook method to look up the converter for a given sourceType/targetType pair.
	 * First queries this ConversionService's converter cache.
	 * On a cache miss, then performs an exhaustive search for a matching converter.
	 * If no converter matches, returns the default converter.
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the generic converter that will perform the conversion,
	 * or {@code null} if no suitable converter was found
	 * @see #getDefaultConverter(TypeDescriptor, TypeDescriptor)
	 */
	@Nullable
	protected GenericUserConfigsMigrator getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		ConverterCacheKey key = new ConverterCacheKey(sourceType, targetType);
		GenericUserConfigsMigrator converter = this.converterCache.get(key);
		if (converter != null) {
			return (converter != NO_MATCH ? converter : null);
		}

		converter = this.migrators.find(sourceType, targetType);
		if (converter == null) {
			converter = getDefaultConverter(sourceType, targetType);
		}

		if (converter != null) {
			this.converterCache.put(key, converter);
			return converter;
		}

		this.converterCache.put(key, NO_MATCH);
		return null;
	}

	/**
	 * Return the default converter if no converter is found for the given sourceType/targetType pair.
	 * <p>Returns a NO_OP Converter if the source type is assignable to the target type.
	 * Returns {@code null} otherwise, indicating no suitable converter could be found.
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the default generic converter that will perform the conversion
	 */
	@Nullable
	protected GenericUserConfigsMigrator getDefaultConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		return (sourceType.isAssignableTo(targetType) ? NO_OP_CONVERTER : null);
	}

	/**
	 * Manages all migrators registered with the service.
	 */
	private static class Migrators {

		private final Set<GenericUserConfigsMigrator> globalConverters = new CopyOnWriteArraySet<>();

		private final Map<MigratablePair, ConvertersForPair> converters = new ConcurrentHashMap<>(256);

		public void add(GenericUserConfigsMigrator converter) {
			Set<MigratablePair> convertibleTypes = converter.getConvertibleTypes();
			if (convertibleTypes == null) {
				// XXX
				Assert.state(converter instanceof ConditionalUserConfigsMigrator,
						"Only conditional converters may return null convertible types");
				this.globalConverters.add(converter);
			}
			else {
				for (MigratablePair convertiblePair : convertibleTypes) {
					getMatchableConverters(convertiblePair).add(converter);
				}
			}
		}

		private ConvertersForPair getMatchableConverters(MigratablePair convertiblePair) {
			return this.converters.computeIfAbsent(convertiblePair, k -> new ConvertersForPair());
		}

		public void remove(Class<?> sourceType, Class<?> targetType) {
			this.converters.remove(new MigratablePair(sourceType, targetType));
		}

		/**
		 * Find a {@link GenericUserConfigsMigrator} given a source and target type.
		 * <p>This method will attempt to match all possible converters by working
		 * through the class and interface hierarchy of the types.
		 * @param sourceType the source type
		 * @param targetType the target type
		 * @return a matching {@link GenericUserConfigsMigrator}, or {@code null} if none found
		 */
		@Nullable
		public GenericUserConfigsMigrator find(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Search the full type hierarchy
			List<Class<?>> sourceCandidates = getClassHierarchy(sourceType.getType());
			List<Class<?>> targetCandidates = getClassHierarchy(targetType.getType());
			for (Class<?> sourceCandidate : sourceCandidates) {
				for (Class<?> targetCandidate : targetCandidates) {
					MigratablePair convertiblePair = new MigratablePair(sourceCandidate, targetCandidate);
					GenericUserConfigsMigrator converter = getRegisteredConverter(sourceType, targetType, convertiblePair);
					if (converter != null) {
						return converter;
					}
				}
			}
			return null;
		}

		@Nullable
		private GenericUserConfigsMigrator getRegisteredConverter(TypeDescriptor sourceType,
				TypeDescriptor targetType, MigratablePair convertiblePair) {

			// Check specifically registered converters
			ConvertersForPair convertersForPair = this.converters.get(convertiblePair);
			if (convertersForPair != null) {
				GenericUserConfigsMigrator converter = convertersForPair.getConverter(sourceType, targetType);
				if (converter != null) {
					return converter;
				}
			}
			// Check ConditionalConverters for a dynamic match
			// XXX
			// for (GenericConverter globalConverter : this.globalConverters) {
			// 	if (((ConditionalConverter) globalConverter).matches(sourceType, targetType)) {
			// 		return globalConverter;
			// 	}
			// }
			return null;
		}

		/**
		 * Returns an ordered class hierarchy for the given type.
		 * @param type the type
		 * @return an ordered list of all classes that the given type extends or implements
		 */
		private List<Class<?>> getClassHierarchy(Class<?> type) {
			List<Class<?>> hierarchy = new ArrayList<>(20);
			Set<Class<?>> visited = new HashSet<>(20);
			addToClassHierarchy(0, ClassUtils.resolvePrimitiveIfNecessary(type), false, hierarchy, visited);
			boolean array = type.isArray();

			int i = 0;
			while (i < hierarchy.size()) {
				Class<?> candidate = hierarchy.get(i);
				candidate = (array ? candidate.getComponentType() : ClassUtils.resolvePrimitiveIfNecessary(candidate));
				Class<?> superclass = candidate.getSuperclass();
				if (superclass != null && superclass != Object.class && superclass != Enum.class) {
					addToClassHierarchy(i + 1, candidate.getSuperclass(), array, hierarchy, visited);
				}
				addInterfacesToClassHierarchy(candidate, array, hierarchy, visited);
				i++;
			}

			if (Enum.class.isAssignableFrom(type)) {
				addToClassHierarchy(hierarchy.size(), Enum.class, array, hierarchy, visited);
				addToClassHierarchy(hierarchy.size(), Enum.class, false, hierarchy, visited);
				addInterfacesToClassHierarchy(Enum.class, array, hierarchy, visited);
			}

			addToClassHierarchy(hierarchy.size(), Object.class, array, hierarchy, visited);
			addToClassHierarchy(hierarchy.size(), Object.class, false, hierarchy, visited);
			return hierarchy;
		}

		private void addInterfacesToClassHierarchy(Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			for (Class<?> implementedInterface : type.getInterfaces()) {
				addToClassHierarchy(hierarchy.size(), implementedInterface, asArray, hierarchy, visited);
			}
		}

		private void addToClassHierarchy(int index, Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			if (asArray) {
				type = Array.newInstance(type, 0).getClass();
			}
			if (visited.add(type)) {
				hierarchy.add(index, type);
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ConversionService converters =\n");
			for (String converterString : getConverterStrings()) {
				builder.append('\t').append(converterString).append('\n');
			}
			return builder.toString();
		}

		private List<String> getConverterStrings() {
			List<String> converterStrings = new ArrayList<>();
			for (ConvertersForPair convertersForPair : this.converters.values()) {
				converterStrings.add(convertersForPair.toString());
			}
			Collections.sort(converterStrings);
			return converterStrings;
		}
	}


	/**
	 * Manages converters registered with a specific {@link MigratablePair}.
	 */
	private static class ConvertersForPair {

		private final Deque<GenericUserConfigsMigrator> converters = new ConcurrentLinkedDeque<>();

		public void add(GenericUserConfigsMigrator converter) {
			this.converters.addFirst(converter);
		}

		@Nullable
		public GenericUserConfigsMigrator getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
			for (GenericUserConfigsMigrator converter : this.converters) {
				// XXX
				if (!(converter instanceof ConditionalGenericUserConfigsMigrator genericConverter) ||
						genericConverter.matches(sourceType, targetType)) {
					return converter;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return StringUtils.collectionToCommaDelimitedString(this.converters);
		}
	}

	/**
	 * Key for use with the converter cache.
	 */
	private static final class ConverterCacheKey implements Comparable<ConverterCacheKey> {

		private final TypeDescriptor sourceType;

		private final TypeDescriptor targetType;

		public ConverterCacheKey(TypeDescriptor sourceType, TypeDescriptor targetType) {
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ConverterCacheKey otherKey)) {
				return false;
			}
			return (this.sourceType.equals(otherKey.sourceType)) &&
					this.targetType.equals(otherKey.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 29 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return ("ConverterCacheKey [sourceType = " + this.sourceType +
					", targetType = " + this.targetType + "]");
		}

		@Override
		public int compareTo(ConverterCacheKey other) {
			int result = this.sourceType.getResolvableType().toString().compareTo(
					other.sourceType.getResolvableType().toString());
			if (result == 0) {
				result = this.targetType.getResolvableType().toString().compareTo(
						other.targetType.getResolvableType().toString());
			}
			return result;
		}
	}

	/**
	 * Internal migrator that performs no operation.
	 */
	private static class NoOpMigrator implements GenericUserConfigsMigrator {

		private final String name;

		public NoOpMigrator(String name) {
			this.name = name;
		}

		@Override
		@Nullable
		public Set<MigratablePair> getConvertibleTypes() {
			return null;
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			return source;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	/**
	 * Adapts a {@link UserConfigsMigrator} to a {@link GenericUserConfigsMigrator}.
	 */
	@SuppressWarnings("unchecked")
	private final class ConverterAdapter implements ConditionalGenericUserConfigsMigrator {

		private final UserConfigsMigrator<Object, Object> converter;

		private final MigratablePair typeInfo;

		private final ResolvableType targetType;

		public ConverterAdapter(UserConfigsMigrator<?, ?> converter, ResolvableType sourceType, ResolvableType targetType) {
			this.converter = (UserConfigsMigrator<Object, Object>) converter;
			this.typeInfo = new MigratablePair(sourceType.toClass(), targetType.toClass());
			this.targetType = targetType;
		}

		@Override
		public Set<MigratablePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Check raw type first...
			if (this.typeInfo.getTargetType() != targetType.getObjectType()) {
				return false;
			}
			// Full check for complex generic type match required?
			ResolvableType rt = targetType.getResolvableType();
			if (!(rt.getType() instanceof Class) && !rt.isAssignableFrom(this.targetType) &&
					!this.targetType.hasUnresolvableGenerics()) {
				return false;
			}
			return !(this.converter instanceof ConditionalUserConfigsMigrator conditionalConverter) ||
					conditionalConverter.matches(sourceType, targetType);
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			if (source == null) {
				return convertNullSource(sourceType, targetType);
			}
			return this.converter.migrate(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converter);
		}
	}
}
