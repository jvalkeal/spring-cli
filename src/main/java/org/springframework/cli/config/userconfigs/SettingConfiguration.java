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
import org.springframework.cli.support.userconfigs.SettingsScan;
import org.springframework.context.annotation.Configuration;

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
public class SettingConfiguration {
}
