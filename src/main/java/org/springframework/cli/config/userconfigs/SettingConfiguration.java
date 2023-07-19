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
package org.springframework.cli.config.userconfigs;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cli.support.userconfigs.DefaultSettingsMigrationService;
import org.springframework.cli.support.userconfigs.DefaultSettingsService;
import org.springframework.cli.support.userconfigs.SettingsHolder;
import org.springframework.cli.support.userconfigs.SettingsMigrationService;
import org.springframework.cli.support.userconfigs.SettingsProperties;
import org.springframework.cli.support.userconfigs.SettingsScan;
import org.springframework.cli.support.userconfigs.SettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Configuration for user configs. Responsible creating
 * {@link UserConfigsService} and automatically scans user config candidate
 * classes.
 *
 * @author Janne Valkealahti
 */
@Configuration(proxyBeanMethods = false)
@SettingsScan
@RegisterReflectionForBinding({ Initializrs.class, Initializrs.Initializr.class })
@EnableConfigurationProperties(SettingsProperties.class)
public class SettingConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SettingsMigrationService userConfigsMigrationService(ObjectProvider<Converter<?, ?>> converters) {
		DefaultConversionService conversionService = new DefaultConversionService();
		converters.forEach(conversionService::addConverter);
		DefaultSettingsMigrationService service = new DefaultSettingsMigrationService(conversionService);
		return service;
	}

	@Bean
	@ConditionalOnMissingBean
	public SettingsService userConfigsService(ObjectProvider<SettingsHolder> userConfigsHolder,
			SettingsProperties properties, SettingsMigrationService migrationService) {
		DefaultSettingsService service = new DefaultSettingsService(properties.getSettingsDirName(),
				properties.getSettingsDirEnv(), migrationService);
		userConfigsHolder.stream()
			.flatMap(uch -> uch.getUserConfigClasses().stream())
			.forEach(type -> {
				service.register(type);
			});
		return service;
	}

}
