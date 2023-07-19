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

import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionService;

public class DefaultSettingsMigrationService implements SettingsMigrationService {

	private final ConversionService conversionService;

	public DefaultSettingsMigrationService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	public boolean canMigrate(Class<?> sourceType, Class<?> targetType) {
		return this.conversionService.canConvert(sourceType, targetType);
	}

	@Override
	public <T> T migrate(Object source, Class<T> targetType) {
		try {
			return this.conversionService.convert(source, targetType);
		} catch (ConversionException e) {
			throw new SettingsMigrationException("Error during migration", e);
		}
	}

}
