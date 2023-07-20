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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of a {@link SettingsService}.
 *
 * This implementation keeps settings persisted in a common location
 * which is different depending on a Operating System in use. A set of
 * files are used and file format is {@code yaml}. Filenames depends
 * of a {@code space} and {@code partition} in use.
 *
 * {@code Settings Directory Name} is mandatory to set so that there is a less
 * change of a multiple implementations picking same settings directory, thus
 * we don't have a default directory.
 *
 * One {@code space} can only use exactly one {@code Java POJO type}, thus
 * a {@code space} is always represented with one instance of a {@code POJO}.
 *
 * @author Janne Valkealahti
 */
public class DefaultSettingsService implements SettingsService {

	private final static Logger log = LoggerFactory.getLogger(DefaultSettingsService.class);
	private final static String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
	private final static String APP_DATA = "APP_DATA";
	private final static String DEFAULT_VERSION = "1";
	private final static String DEFAULT_VERSION_FIELD = "version";

	private final String settingsDirEnv;
	private final String settingsDirName;
	private final ObjectMapper objectMapper;
	private final SettingsMigrationService migrationService;

	private Function<String, Path> pathProvider = (path) -> Paths.get(path);

	public DefaultSettingsService(String settingsDirName, @Nullable String settingsDirEnv,
			SettingsMigrationService migrationService) {
		Assert.hasText(settingsDirName, "settings directory name must be set");
		this.settingsDirEnv = settingsDirEnv;
		this.settingsDirName = settingsDirName;
		this.migrationService = migrationService;
		this.objectMapper = defaultObjectMapper();
	}

	public void register(Class<?> type) {
		SettingsBinding sbAnn = AnnotationUtils.findAnnotation(type, SettingsBinding.class);
		Assert.notNull(sbAnn, "Class needs to have SettingsBindings annotation");
		register(type, sbAnn.partition(), sbAnn.space(), sbAnn.version(), sbAnn.field());
	}

	// space -> partition -> clazz -> (version, field)
	private final Map<String, Map<String, Map<Class<?>, Partition>>> spaceMappings = new HashMap<>();

	private record Partition(int version, String field) {
	}

	private record PartitionInfo(String name, Partition partition){}

	private void register(Class<?> type, String partition, @Nullable String space, @Nullable int version,
			@Nullable String field) {
		log.debug("Registering class {}", type);
		space = StringUtils.hasText(space) ? space : null;
		String fieldx = StringUtils.hasText(field) ? field : DEFAULT_VERSION_FIELD;

		Map<String, Map<Class<?>, Partition>> spaceMapping = spaceMappings.computeIfAbsent(space,
				key -> new HashMap<>());
		Map<Class<?>, Partition> classMapping = spaceMapping.computeIfAbsent(partition, key -> new HashMap<>());
		classMapping.computeIfAbsent(type, key -> new Partition(version, fieldx));
	}

	private PartitionInfo checkRegistered(Class<?> type, String space) {
		Map<String, Map<Class<?>, Partition>> spaceMapping = spaceMappings.get(space);
		Assert.notNull(spaceMapping, () -> String.format("No classes registered with space %s", space));
		for (Entry<String, Map<Class<?>, Partition>> entry1 : spaceMapping.entrySet()) {
			for (Entry<Class<?>, Partition> entry2 : entry1.getValue().entrySet()) {
				if (entry2.getKey() == type) {
					return new PartitionInfo(entry1.getKey(), entry2.getValue());
				}
			}
		}
		return null;
	}

	@Override
	public <T> T read(Class<T> type) {
		return read(type, null, null);
	}

	@Override
	public <T> T read(Class<T> type, Supplier<T> defaultSupplier) {
		return read(type, null, defaultSupplier);
	}

	@Override
	public <T> T read(Class<T> type, String space) {
		return read(type, space, null);
	}

	@Override
	public <T> T read(Class<T> type, String space, Supplier<T> defaultSupplier) {
		String spaceName = StringUtils.hasText(space) ? space : SettingsService.DEFAULT_SPACE;
		PartitionInfo info = checkRegistered(type, spaceName);

		// int[] xxx = xxx(spaceName, info.name());
		int toVersion = info.partition.version();
		XxxInfo[] xxx2 = xxx2(spaceName, info.name());
		// if (toVersion >)

		Path path = resolvePath(type, space, info.name, info.partition().version());

		T obj = null;
		if (Files.exists(path)) {
			obj = doRead(path, type, info.partition().field());
			if (obj == null && defaultSupplier != null) {
				obj = defaultSupplier.get();
			}
		}
		else {
			try {
				obj = migrate(type, spaceName, info.name(), toVersion, xxx2);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}


		// T obj = doRead(path, type, info.partition().field());
		// if (obj == null && defaultSupplier != null) {
		// 	obj = defaultSupplier.get();
		// }
		return obj;
	}

	private <T> T migrate(Class<T> typex, String spaceName, String partitionName, int toVersion, XxxInfo[] xxx2)
			throws IOException {

		T objx = null;
		Object obj = null;
		Class<?> sourceType = null;
		for (XxxInfo xxx : xxx2) {
			sourceType = xxx.type();
			Class<?> targetType = null;
			if (toVersion == xxx.version()) {

			}
			else if (toVersion > xxx.version()) {

			}
			Class<?> type = xxx.type();
			int version = xxx.version();
			Path path = resolvePath(type, spaceName, partitionName, version);
			if (Files.exists(path) && typex == type) {
				return doRead(path, typex, partitionName);
			}
			else {
				boolean canMigrate = migrationService.canMigrate(sourceType, targetType);
				if (canMigrate) {
					InputStream in = new DataInputStream(Files.newInputStream(path));
					ObjectNode objectNode = objectMapper.readValue(in, ObjectNode.class);
					// JsonNode versionNode = objectNode.get(field);

					Object treeToValue = objectMapper.treeToValue(objectNode, sourceType);
					obj = migrationService.migrate(treeToValue, targetType);
				}

			}
			if (toVersion == xxx.version()) {
				objx = (T)obj;
			}
		}

		return objx;
	}

	@Override
	public void write(Object value) {
		write(value, null);
	}


	private Path resolvePath(Class<?> type, String space, String partition, int version) {
		String spaceName = StringUtils.hasText(space) ? space : SettingsService.DEFAULT_SPACE;
		String name = spaceName + "-" + partition + "-v" + version + ".yml";
		Path dir = getConfigDir();
		return dir.resolve(name);
	}

	@Override
	public void write(Object value, String space) {
		log.debug("Writing");
		Assert.notNull(value, "value cannot be null");
		String spaceName = StringUtils.hasText(space) ? space : SettingsService.DEFAULT_SPACE;
		PartitionInfo info = checkRegistered(value.getClass(), spaceName);
		Path path = resolvePath(value.getClass(), space, info.name, info.partition().version());
		doWrite(path, value, info);
	}

	public void setPathProvider(Function<String, Path> pathProvider) {
		this.pathProvider = pathProvider;
	}

	private record XxxInfo(Class<?> type, int version) implements Comparable<XxxInfo> {

		@Override
		public int compareTo(XxxInfo other) {
			return Integer.compare(version, other.version());
		}
	}

	private XxxInfo[] xxx2(String space, String partition) {
		Map<String, Map<Class<?>, Partition>> spaceMapping = spaceMappings.get(space);
		Map<Class<?>, Partition> classMapping = spaceMapping.get(partition);
		return classMapping.entrySet().stream()
			.map(e -> new XxxInfo(e.getKey(), e.getValue().version()))
			.sorted()
			.toArray(XxxInfo[]::new)
			;
	}


	private int[] xxx(String space, String partition) {
		Map<String, Map<Class<?>, Partition>> spaceMapping = spaceMappings.get(space);
		Map<Class<?>, Partition> classMapping = spaceMapping.get(partition);
		for (Entry<Class<?>, Partition> entry : classMapping.entrySet()) {
			entry.getValue().version();
		}
		int[] arr = classMapping.entrySet().stream()
			.mapToInt(e -> e.getValue().version())
			.sorted()
			.toArray();
		return arr;
	}


	private <T> T doRead(Path path, Class<T> type, String field) {
		log.debug("About to read type {} from path {}", type, path);

		// for a space and partition get existing versions.

		if (!Files.exists(path)) {
			log.debug("Path {} does not exist", path);
			return null;
		}
		try {
			InputStream in = new DataInputStream(Files.newInputStream(path));

			ObjectNode objectNode = objectMapper.readValue(in, ObjectNode.class);
			JsonNode versionNode = objectNode.get(field);
			String version = null;
			if (versionNode != null) {
				version = versionNode.asText();
				objectNode.remove(field);
			}
			String versionx = version;

			// Class<?> sourceType = info.versions.entrySet().stream().filter(e -> e.getValue().equals(versionx))
			// 		.map(e -> e.getKey()).findFirst().orElse(null);
			// if (migrationService != null) {
			// 	Class<?> targetType = type;
			// 	boolean canMigrate = migrationService.canMigrate(sourceType, targetType);
			// 	if (canMigrate) {
			// 		Object treeToValue = objectMapper.treeToValue(objectNode, sourceType);
			// 		return migrationService.migrate(treeToValue, type);
			// 	}

			// }

			// objectNode = migration(objectNode, version, info.versions, info.migration);
			return objectMapper.treeToValue(objectNode, type);
		} catch (Exception e) {
			throw new RuntimeException("Unable to read from path " + path, e);
		}
	}

	private void doWrite(Path path, Object value, PartitionInfo info) {
		log.debug("About to write value {} to path {}", value, path);
		try {
			Path parentDir = path.getParent();
			if (!Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}
			OutputStream out = new DataOutputStream(Files.newOutputStream(path));

			ObjectNode objectNode = objectMapper.valueToTree(value);
			// Integer v = info.versions.get(value.getClass());
			objectNode.put(info.partition().field(), info.partition().version());
			objectMapper.writeValue(out, objectNode);
		} catch (Exception e) {
			throw new RuntimeException("Unable to write to path " + path, e);
		}
	}

	private Path getConfigDir() {
		Path path;
		if (StringUtils.hasText(System.getenv(settingsDirEnv))) {
			path = pathProvider.apply(System.getenv(settingsDirEnv));
		}
		else if (StringUtils.hasText(System.getenv(XDG_CONFIG_HOME))) {
			path = pathProvider.apply(System.getenv(XDG_CONFIG_HOME)).resolve(settingsDirName);
		}
		else if (isWindows() && StringUtils.hasText(System.getenv(APP_DATA))) {
			path = pathProvider.apply(System.getenv(APP_DATA)).resolve(settingsDirName);
		}
		else {
			path = pathProvider.apply(System.getProperty("user.home")).resolve(".config").resolve(settingsDirName);
		}
		return path;
	}

	private boolean isWindows() {
		String os = System.getProperty("os.name");
		return os.startsWith("Windows");
	}

	private static ObjectMapper defaultObjectMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(Include.NON_NULL);
		return mapper;
	}
}
