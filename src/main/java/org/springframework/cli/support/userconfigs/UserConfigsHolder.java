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

import java.util.Set;

/**
 * Bean class holding set of candicate classes for user configs.
 *
 * @author Janne Valkealahti
 */
public class UserConfigsHolder {

	private Set<Class<?>> userConfigClasses;

	public UserConfigsHolder(Set<Class<?>> userConfigClasses) {
		this.userConfigClasses = userConfigClasses;
	}

	public Set<Class<?>> getUserConfigClasses() {
		return userConfigClasses;
	}
}
