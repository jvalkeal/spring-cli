package org.springframework.cli.support.userconfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cli.support.userconfigs.config1.Pojo1;
import org.springframework.cli.support.userconfigs.config2.Pojo2;
import org.springframework.cli.support.userconfigs.config2.Pojo3;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

class DefaultUserConfigsTests {

	private Path tempDir;
	private DefaultUserConfigsService service;
	private ConfigurableApplicationContext context;

	@BeforeEach
	void setup(@TempDir Path tempDir) {
		this.tempDir = tempDir;
		this.service = new DefaultUserConfigsService("test", "TEST_DIR");
		service.setPathProvider(path -> tempDir);
	}

	@AfterEach
	void clean() {
		if (context != null) {
			context.close();
		}
		context = null;
	}

	@Test
	void settingsDirectoryNameMustBeSet() {
		assertThrows(IllegalArgumentException.class, () -> {
			new DefaultUserConfigsService(null, null);
		});
		assertThrows(IllegalArgumentException.class, () -> {
			new DefaultUserConfigsService("", null);
		});
	}

	@Test
	void typeNeedsToBeRegistered() {
		assertThrows(IllegalArgumentException.class, () -> {
			service.read(Object.class);
		});
		assertThrows(IllegalArgumentException.class, () -> {
			service.write(new Object());
		});
	}

	@Test
	void notYetWrittenShouldReturnNullForRead() {
		service.register(Pojo1.class);
		Pojo1 read = service.read(Pojo1.class);
		assertThat(read).isNull();
	}

	@Test
	void shouldStoreInDefaultSpace() {
		service.register(Pojo1.class);
		Pojo1 write = Pojo1.of("hi");
		service.write(write);
		Pojo1 read = service.read(Pojo1.class);
		assertThat(write).isEqualTo(read);
	}

	@Test
	void shouldStoreSameTypeInMultipleSpaces() {
		service.register(Pojo1.class, "space1", null, null, null, null);
		service.register(Pojo1.class, "space2", null, null, null, null);

		Pojo1 writeSpace1 = Pojo1.of("hi1");
		service.write(writeSpace1, "space1");

		Pojo1 writeSpace2 = Pojo1.of("hi2");
		service.write(writeSpace2, "space2");

		Pojo1 readSpace1 = service.read(Pojo1.class, "space1");
		Pojo1 readSpace2 = service.read(Pojo1.class, "space2");
		assertThat(writeSpace1).isEqualTo(readSpace1);
		assertThat(writeSpace2).isEqualTo(readSpace2);
		assertThat(readSpace1.getField1()).isEqualTo("hi1");
		assertThat(readSpace2.getField1()).isEqualTo("hi2");
	}

	@SuppressWarnings("unused")
	private static class MigrationPojo1 {

		String field2;

		public MigrationPojo1() {
		}

		public MigrationPojo1(String field2) {
			this.field2 = field2;
		}

		public String getField2() {
			return field2;
		}

		public void setField2(String field2) {
			this.field2 = field2;
		}
	}

	private static class MigrationPojo1SettingsMigration implements UserConfigsMigration {

		@Override
		public ObjectNode migrate(ObjectNode objectNode, String fromVersion, String toVersion) {
			if ("2".equals(toVersion)) {
				JsonNode node = objectNode.get("field1");
				if (node != null) {
					objectNode.remove("field1");
				}
				objectNode.put("field2", "value2");
			}
			return objectNode;
		}

	}

	@Test
	void shouldMigrateFromV1ToV2WhenRead() throws IOException {
		String v1Yaml =
				"""
				---
				version: 1
				field1: value1
				""";
		Path dir = tempDir.resolve(".config").resolve("test");
		Files.createDirectories(dir);
		Files.write(dir.resolve("default-space-DefaultUserConfigsTests.MigrationPojo1"),
				v1Yaml.getBytes());
		Set<String> versions = new HashSet<>(Arrays.asList("1", "2"));
		service.register(MigrationPojo1.class, null, versions, "2", null, new MigrationPojo1SettingsMigration());
		MigrationPojo1 pojo = service.read(MigrationPojo1.class);
		assertThat(pojo).isNotNull();
		assertThat(pojo.getField2()).isEqualTo("value2");
	}

	@Test
	void test() {
		context = new AnnotationConfigApplicationContext(TestConfig.class);
		UserConfigsService userConfigsService = context.getBean(UserConfigsService.class);
		Pojo1 write = Pojo1.of("hi");
		userConfigsService.write(write);
		Pojo1 read = userConfigsService.read(Pojo1.class);
		assertThat(write).isEqualTo(read);

	}

	@EnableUserConfigs(Pojo1.class)
	@Import(UserConfigsConfiguration.class)
	private static class TestConfig {

		// @Bean
		// UserConfigsService userConfigsService(ObjectProvider<UserConfigsHolder> userConfigsHolder) {
		// 	DefaultUserConfigsService service = new DefaultUserConfigsService("test", "TEST_DIR");
		// 	userConfigsHolder.stream()
		// 		.flatMap(uch -> uch.getUserConfigClasses().stream())
		// 		.forEach(type -> {
		// 			service.register(type);
		// 		});
		// 	return service;
		// }
	}

	@Test
	void test2() {
		context = new AnnotationConfigApplicationContext(TestConfig2.class);
		UserConfigsService userConfigsService = context.getBean(UserConfigsService.class);

		Pojo2 write2 = Pojo2.of("hi");
		userConfigsService.write(write2);
		Pojo2 read2 = userConfigsService.read(Pojo2.class);
		assertThat(write2).isEqualTo(read2);

		Pojo3 write3 = Pojo3.of("hi");
		userConfigsService.write(write3);
		Pojo3 read3 = userConfigsService.read(Pojo3.class);
		assertThat(write3).isEqualTo(read3);
	}

	@UserConfigsScan(basePackages = "org.springframework.cli.support.xxx.config2")
	@Import(UserConfigsConfiguration.class)
	private static class TestConfig2 {

		// @Bean
		// UserConfigsService userConfigsService(ObjectProvider<UserConfigsHolder> userConfigsHolder) {
		// 	DefaultUserConfigsService service = new DefaultUserConfigsService("test", "TEST_DIR");
		// 	userConfigsHolder.stream()
		// 		.flatMap(uch -> uch.getUserConfigClasses().stream())
		// 		.forEach(type -> {
		// 			service.register(type);
		// 		});
		// 	return service;
		// }
	}

	private static class UserConfigsConfiguration {

		@Bean
		UserConfigsService userConfigsService(ObjectProvider<UserConfigsHolder> userConfigsHolder) {
			DefaultUserConfigsService service = new DefaultUserConfigsService("test", "TEST_DIR");
			userConfigsHolder.stream()
				.flatMap(uch -> uch.getUserConfigClasses().stream())
				.forEach(type -> {
					service.register(type);
				});
			return service;
		}
	}
}
