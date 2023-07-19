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

import java.util.HashMap;
import java.util.Map;

import org.springframework.cli.support.userconfigs.Settings;

/**
 * User settigs for {@code initializr} commands.
 *
 * @author Janne Valkealahti
 */
@Settings(version = "1")
public class Initializrs {

	private Map<String, Initializr> initializrs = new HashMap<>();

	public Initializrs() {
	}

	public Initializrs(Map<String, Initializr> initializrs) {
		this.initializrs.putAll(initializrs);
	}

	public Map<String, Initializr> getInitializrs() {
		return initializrs;
	}

	public static Initializrs of(Map<String, Initializr> initializrs) {
		return new Initializrs(initializrs);
	}

	public void setInitializrs(Map<String, Initializr> initializrs) {
		this.initializrs = initializrs;
	}

	public static class Initializr {

		private String url;

		Initializr() {
		}

		public Initializr(String url) {
			this.url = url;
		}

		public static Initializr of(String url) {
			return new Initializr(url);
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}
	}
}
