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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cli.support.userconfigs.migration.UserConfigsMigrationService;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of a {@link UserConfigsService}.
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
 * One {@code space} can only use excatly one {@code Java POJO type}, thus
 * a {@code space} is always represented with one instance of a {@code POJO}.
 *
 * @author Janne Valkealahti
 */
public class DefaultUserConfigsService implements UserConfigsService {

	private final static Logger log = LoggerFactory.getLogger(DefaultUserConfigsService.class);
	private final static String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
	private final static String APP_DATA = "APP_DATA";
	private final static String DEFAULT_VERSION = "1";
	private final static String DEFAULT_VERSION_FIELD = "version";

	private final String settingsDirEnv;
	private final String settingsDirName;
	private final ObjectMapper objectMapper;
	private final UserConfigsMigrationService migrationService;

	private Function<String, Path> pathProvider = (path) -> Paths.get(path);

	public DefaultUserConfigsService(String settingsDirName, @Nullable String settingsDirEnv,
			UserConfigsMigrationService migrationService) {
		Assert.hasText(settingsDirName, "settings directory name must be set");
		this.settingsDirEnv = settingsDirEnv;
		this.settingsDirName = settingsDirName;
		this.migrationService = migrationService;
		this.objectMapper = defaultObjectMapper();
	}

	public void register(Class<?> type) {
		UserConfigs sbAnn = AnnotationUtils.findAnnotation(type, UserConfigs.class);
		Assert.notNull(sbAnn, "Class needs to have SettingsBindings annotation");
		register(type, sbAnn.partition(), sbAnn.space(), new HashSet<>(Arrays.asList(sbAnn.versions())), sbAnn.version(), sbAnn.field());
	}

	private final Map<String, Map<String, PartitionInfo>> spaceBindings = new HashMap<>();

	private static class PartitionInfo {

		String version;
		// Set<String> versions = new HashSet<>();
		Map<Class<?>, String> versions = new HashMap<>();
		String field;
		String partition;
	}

	private void register(Class<?> type, String partition, @Nullable String space, Set<String> versions, @Nullable String version,
			@Nullable String field) {
		log.debug("Registering class {}", type);
		space = StringUtils.hasText(space) ? space : null;
		field = StringUtils.hasText(field) ? field : DEFAULT_VERSION_FIELD;
		version = StringUtils.hasText(version) ? version : DEFAULT_VERSION;
		if (versions == null) {
			versions = new HashSet<>();
		}
		versions.add(version);

		Map<String, PartitionInfo> spaceBinding = spaceBindings.computeIfAbsent(space, key -> new HashMap<>());
		PartitionInfo partitionInfo = spaceBinding.computeIfAbsent(partition, key -> new PartitionInfo());
		partitionInfo.version = version;
		// partitionInfo.versions.add(version);
		partitionInfo.versions.put(type, version);
		partitionInfo.field = field;
		partitionInfo.partition = partition;

		// Map<String, PartitionInfo2> partitionBinding = spaceBindings.computeIfAbsent(partition, key -> new HashMap<>());
		// spaceBinding.put(partition, partitionInfo2);

		// Map<Class<?>, SpaceTypeInfo> spaceBinding = spaceBindings.computeIfAbsent(space, key -> new HashMap<>());
		// SpaceTypeInfo spaceTypeInfo = new SpaceTypeInfo(version, versions, field, migration);
		// spaceBinding.put(type, spaceTypeInfo);
	}

	private PartitionInfo checkRegistered(Class<?> type, String space) {
		Map<String, PartitionInfo> map1 = spaceBindings.get(space);
		Assert.notNull(map1, () -> String.format("No classes registered with space %s", space));
		PartitionInfo orElse = map1.entrySet().stream()
			.filter(e -> e.getValue().versions.keySet().contains(type))
			.map(e -> e.getValue())
			.findFirst()
			.orElse(null)
			;

		return orElse;
	}

	// private SpaceTypeInfo checkRegistered(Class<?> type, String space) {
	// 	Map<Class<?>, SpaceTypeInfo> spaceBinding = spaceBindings.get(space);
	// 	Assert.notNull(spaceBinding, () -> String.format("No classes registered with space %s", space));
	// 	SpaceTypeInfo info = spaceBinding.get(type);
	// 	Assert.isTrue(type != null,
	// 			() -> String.format("Class type %s not registered in space %s", type, space));
	// 	return info;
	// }

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
		PartitionInfo info = checkRegistered(type, space);
		Path path = resolvePath(type, space, info.partition);
		T obj = doRead(path, type, info);
		if (obj == null && defaultSupplier != null) {
			obj = defaultSupplier.get();
		}
		return obj;
	}

	@Override
	public void write(Object value) {
		write(value, null);
	}


	private Path resolvePath(Class<?> type, String space, String partition) {
		String spaceName = StringUtils.hasText(space) ? space : "default-space";
		// String name = spaceName + "-" + nameProvider.apply(type);
		String name = spaceName + "-" + partition;
		Path dir = getConfigDir();
		return dir.resolve(name);
	}

	@Override
	public void write(Object value, String space) {
		log.debug("Writing");
		Assert.notNull(value, "value cannot be null");
		PartitionInfo info = checkRegistered(value.getClass(), space);
		checkRegistered(value.getClass(), space);
		Path path = resolvePath(value.getClass(), space, info.partition);
		doWrite(path, value, info);
	}

	public void setPathProvider(Function<String, Path> pathProvider) {
		this.pathProvider = pathProvider;
	}

	// private ObjectNode migration(ObjectNode objectNode, String fromVersion, Set<String> versions, UserConfigsMigration migration) {
	// 	if (migration == null) {
	// 		return objectNode;
	// 	}
	// 	List<String> migrateVersions = versions.stream()
	// 		.filter(v -> v.compareTo(fromVersion) > 0)
	// 		.sorted()
	// 		.collect(Collectors.toList());
	// 	String from = fromVersion;
	// 	String to = null;
	// 	for (String version : migrateVersions) {
	// 		to = version;
	// 		ObjectNode migratedObjectNode = migration.migrate(objectNode, from, to);
	// 		if (migratedObjectNode == null) {
	// 			return objectNode;
	// 		}
	// 		objectNode = migratedObjectNode;
	// 		from = version;
	// 	}
	// 	return objectNode;
	// }


	private <T> T doRead(Path path, Class<T> type, PartitionInfo info) {
		log.debug("About to read type {} from path {}", type, path);
		if(!Files.exists(path)) {
			log.debug("Path {} does not exist", path);
			return null;
		}
		try {
			InputStream in = new DataInputStream(Files.newInputStream(path));

			ObjectNode objectNode = objectMapper.readValue(in, ObjectNode.class);
			JsonNode versionNode = objectNode.get(info.field);
			String version = null;
			if (versionNode != null) {
				version = versionNode.asText();
				objectNode.remove(info.field);
			}
			String versionx = version;

			Class<?> sourceType = info.versions.entrySet().stream().filter(e -> e.getValue().equals(versionx)).map(e -> e.getKey()).findFirst().orElse(null);

			if (migrationService != null) {
				Class<?> targetType = type;
				boolean canMigrate = migrationService.canMigrate(sourceType, targetType);
				if (canMigrate) {
					Object treeToValue = objectMapper.treeToValue(objectNode, sourceType);
					return migrationService.migrate(treeToValue, type);
				}

			}

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
			objectNode.put(info.field, info.version);
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
