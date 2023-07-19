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
package org.springframework.cli.support.userconfigs;

import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * {@code SettingsService} is an interface providing service storing settings
 * using plain {@code POJO} model.
 *
 * Concept of a {@code space} is used as a distinction of a {@code POJO} types
 * so that same {@code POJO} can be stored in a different {@code space}. This
 * means that only one instance of a give class can be stored in a same
 * {@code space}.
 *
 * {@code space} type is {@code String} which is used for identification.
 * If {@code space} type is {@code null} it means a default space which is
 * left for implementation how it is persisted.
 *
 * How settings are persisted is left for the implementation.
 *
 * @author Janne Valkealahti
 */
public interface SettingsService {

	/**
	 * Read user configs from a storage.
	 *
	 * @param <T> the type of object to read
	 * @param type the type of object to read
	 * @return object to read
	 * @see #read(Class, String, Supplier)
	 */
	<T> T read(Class<T> type);

	/**
	 * Read user configs from a storage.
	 *
	 * @param <T> the type of object to read
	 * @param type the type of object to read
	 * @param defaultSupplier the supplier for object if it doesn't exist
	 * @return object to read
	 * @see #read(Class, String, Supplier)
	 */
	<T> T read(Class<T> type, @Nullable Supplier<T> defaultSupplier);

	/**
	 *
	 * @param <T> the type of object to read
	 * @param type the type of object to read
	 * @param space the space to read from
	 * @return object to read
	 * @see #read(Class, String, Supplier)
	 */
	<T> T read(Class<T> type, @Nullable String space);

	/**
	 * Read user configs from a storage.
	 *
	 * @param <T> the type of object to read
	 * @param type the type of object to read
	 * @param space the space to read from
	 * @param defaultSupplier the supplier for object if it doesn't exist
	 * @return object to read
	 */
	<T> T read(Class<T> type, @Nullable String space, @Nullable Supplier<T> defaultSupplier);

	/**
	 * Write user configs into a storage. Uses default {@code space}.
	 *
	 * @param value the value to write
	 * @see #write(Object, String)
	 */
	void write(Object value);

	/**
	 * Write user configs into a storage.
	 *
	 * @param value the value to write
	 * @param space the space to write value
	 */
	void write(Object value, @Nullable String space);
}
