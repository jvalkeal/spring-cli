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
import org.springframework.cli.support.userconfigs.migration.DefaultUserConfigsMigrationService;
import org.springframework.cli.support.userconfigs.migration.UserConfigsMigrationService;
import org.springframework.cli.support.userconfigs.migration.UserConfigsMigrator;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@code User Configs}.
 *
 * @author Janne Valkealahti
 */
@AutoConfiguration
@ConditionalOnClass(UserConfigsService.class)
@EnableConfigurationProperties(UserConfigsProperties.class)
public class UserConfigsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public UserConfigsMigrationService userConfigsMigrationService(ObjectProvider<UserConfigsMigrator<?, ?>> migrators) {
		DefaultUserConfigsMigrationService service = new DefaultUserConfigsMigrationService();
		migrators.forEach(service::addMigrator);
		return service;
	}

	@Bean
	@ConditionalOnMissingBean
	public UserConfigsService userConfigsService(ObjectProvider<UserConfigsHolder> userConfigsHolder,
			UserConfigsProperties properties, UserConfigsMigrationService migrationService) {
		DefaultUserConfigsService service = new DefaultUserConfigsService(properties.getSettingsDirName(),
				properties.getSettingsDirEnv(), migrationService);
		userConfigsHolder.stream()
			.flatMap(uch -> uch.getUserConfigClasses().stream())
			.forEach(type -> {
				service.register(type);
			});
		return service;
	}
}
