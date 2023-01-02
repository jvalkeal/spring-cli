package org.springframework.cli.support.xxx;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of a {@link SettingsService}.
 *
 * This implementation keeps settings persisted in a common location
 * which is different depending on a Operating System in use. A set of
 * files are used and file format is {@code yaml}. Filenames depends
 * of a {@code space} in use.
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
public class DefaultSettingsService implements SettingsService {

	private final static String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
	private final static String APP_DATA = "APP_DATA";

	private final String settingsDirEnv;
	private final String settingsDirName;
	private final ObjectMapper objectMapper;

	private Function<Class<?>, String> nameProvider = clazz -> ClassUtils.getShortName(clazz);
	private Function<String, Path> pathProvider = (path) -> Paths.get(path);

	public DefaultSettingsService(String settingsDirName, @Nullable String settingsDirEnv,
			@Nullable ObjectMapper objectMapper) {
		Assert.hasText(settingsDirName, "settings directory name must be set");
		this.settingsDirEnv = settingsDirEnv;
		this.settingsDirName = settingsDirName;
		this.objectMapper = objectMapper != null ? objectMapper : defaultObjectMapper();
	}

	private static ObjectMapper defaultObjectMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(Include.NON_NULL);
		return mapper;
	}

	private Map<String, Map<Integer, Class<?>>> spaceBindings = new HashMap<>();

	public void register() {

	}

	@Override
	public <T> T read(Class<T> type) {
		return read(type, null);
	}

	@Override
	public <T> T read(Class<T> type, String space) {
		Path dir = getConfigDir();
		String spaceName = StringUtils.hasText(space) ? space : "default-space";
		String name = spaceName + "-" + nameProvider.apply(type);
		Path path = dir.resolve(name);
		return doRead(path, type);
	}

	@Override
	public <T> SpaceResult<T> readResult(Class<T> type, String space, int version) {
		int v = version > 0 ? version : 1;
		Path dir = getConfigDir();
		String spaceName = StringUtils.hasText(space) ? space : "default-space";
		String name = spaceName + "-" + nameProvider.apply(type);
		Path path = dir.resolve(name);
		return null;
	}

	@Override
	public void write(Object value) {
		write(value, null);
	}

	@Override
	public void write(Object value, String space) {
		Path dir = getConfigDir();
		Class<?> clazz = value.getClass();
		String spaceName = StringUtils.hasText(space) ? space : "default-space";
		String name = spaceName + "-" + nameProvider.apply(clazz);
		Path path = dir.resolve(name);
		write(path, value);
	}

	@Override
	public <T> SpaceResult<T> writeResult(Object value, String space, Class<T> type, int version) {
		return null;
	}

	private final Map<String, SpaceState> spaceStates = new HashMap<>();

	@Override
	public SpaceState state(String space) {
		SpaceState spaceState = spaceStates.get(space);
		if (spaceState == null) {
			return SpaceState.UNKNOWN;
		}
		return spaceState;
	}

	public void setPathProvider(Function<String, Path> pathProvider) {
		this.pathProvider = pathProvider;
	}

	private <T> T doRead(Path path, Class<T> type) {
		try {
			InputStream in = new DataInputStream(Files.newInputStream(path));
			return objectMapper.readValue(in, type);
		} catch (Exception e) {
			throw new RuntimeException("Unable to read from path " + path, e);
		}
	}

	private void write(Path path, Object value) {
		try {
			Path parentDir = path.getParent();
			if (!Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}
			OutputStream out = new DataOutputStream(Files.newOutputStream(path));
			objectMapper.writeValue(out, value);
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
}
