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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cli.support.userconfigs.migration.DefaultSettingsMigrationService;
import org.springframework.cli.support.userconfigs.migration.SettingsMigrationService;
import org.springframework.cli.support.userconfigs.migration.SettingsMigrator;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@code User Configs}.
 *
 * @author Janne Valkealahti
 */
@AutoConfiguration
@ConditionalOnClass(SettingsService.class)
@EnableConfigurationProperties(SettingsProperties.class)
public class SettingsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SettingsMigrationService userConfigsMigrationService(ObjectProvider<SettingsMigrator<?, ?>> migrators) {
		DefaultSettingsMigrationService service = new DefaultSettingsMigrationService();
		migrators.forEach(service::addMigrator);
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
