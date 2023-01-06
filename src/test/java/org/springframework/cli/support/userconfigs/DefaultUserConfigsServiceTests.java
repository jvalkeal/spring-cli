package org.springframework.cli.support.userconfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cli.support.userconfigs.config3.Pojo4;
import org.springframework.cli.support.userconfigs.config3.Pojo5;
import org.springframework.cli.support.userconfigs.migration.DefaultUserConfigsMigrationService;
import org.springframework.cli.support.userconfigs.migration.UserConfigsMigrator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

public class DefaultUserConfigsServiceTests {

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
	void shouldStoreInDefaultSpace() {
		service.register(Pojo4.class);

		Pojo4 write = new Pojo4();
		write.setField1("hi");
		service.write(write, null);

		Pojo4 read = service.read(Pojo4.class, null, null);
		assertThat(read).isNotNull();
		assertThat(read.getField1()).isEqualTo("hi");
	}

	@Test
	void shouldMigrate() throws IOException {
		String v1Yaml =
				"""
				---
				version: 1
				field1: value1
				""";
		Path dir = tempDir.resolve(".config").resolve("test");
		Files.createDirectories(dir);
		Files.write(dir.resolve("default-space-p1"),	v1Yaml.getBytes());


		DefaultUserConfigsMigrationService migrationService = new DefaultUserConfigsMigrationService();
		migrationService.addConverter(Pojo4.class, Pojo5.class, new Pojo4ToPojo5Migration());
		service.migrationService = migrationService;

		service.register(Pojo4.class);
		service.register(Pojo5.class);
		Pojo5 pojo = service.read(Pojo5.class, null, null);
		assertThat(pojo).isNotNull();
		assertThat(pojo.getField2()).isEqualTo("value1");


	}

	private static class Pojo4ToPojo5Migration implements UserConfigsMigrator<Pojo4, Pojo5> {

		@Override
		public Pojo5 migrate(Pojo4 source) {
			Pojo5 pojo5 = new Pojo5();
			pojo5.setField2(source.getField1());
			return pojo5;
		}
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
		service.register(Pojo4.class);
		Pojo4 read = service.read(Pojo4.class);
		assertThat(read).isNull();
	}

	// @Test
	// void shouldStoreSameTypeInMultipleSpaces() {
	// 	service.register(Pojo1.class, "space1", null, null, null, null);
	// 	service.register(Pojo1.class, "space2", null, null, null, null);

	// 	Pojo1 writeSpace1 = Pojo1.of("hi1");
	// 	service.write(writeSpace1, "space1");

	// 	Pojo1 writeSpace2 = Pojo1.of("hi2");
	// 	service.write(writeSpace2, "space2");

	// 	Pojo1 readSpace1 = service.read(Pojo1.class, "space1");
	// 	Pojo1 readSpace2 = service.read(Pojo1.class, "space2");
	// 	assertThat(writeSpace1).isEqualTo(readSpace1);
	// 	assertThat(writeSpace2).isEqualTo(readSpace2);
	// 	assertThat(readSpace1.getField1()).isEqualTo("hi1");
	// 	assertThat(readSpace2.getField1()).isEqualTo("hi2");
	// }

	@Test
	void shouldImportFromAnnotation() {
		context = new AnnotationConfigApplicationContext(TestConfig.class);
		UserConfigsService userConfigsService = context.getBean(UserConfigsService.class);
		Pojo4 write4 = new Pojo4();
		write4.setField1("write4");
		userConfigsService.write(write4);
		Pojo4 read4 = userConfigsService.read(Pojo4.class);
		assertThat(write4.getField1()).isEqualTo(read4.getField1());
	}

	@EnableUserConfigs(Pojo4.class)
	@Import(UserConfigsConfiguration.class)
	private static class TestConfig {
	}

	@Test
	void shouldImportFromScan() {
		context = new AnnotationConfigApplicationContext(TestConfig2.class);
		UserConfigsService userConfigsService = context.getBean(UserConfigsService.class);

		Pojo4 write4 = new Pojo4();
		write4.setField1("write4");
		userConfigsService.write(write4);
		Pojo4 read4 = userConfigsService.read(Pojo4.class);
		assertThat(write4.getField1()).isEqualTo(read4.getField1());

		Pojo5 write5 = new Pojo5();
		write5.setField2("write5");
		userConfigsService.write(write5);
		Pojo5 read5 = userConfigsService.read(Pojo5.class);
		assertThat(write5.getField2()).isEqualTo(read5.getField2());

	}

	@UserConfigsScan(basePackages = "org.springframework.cli.support.userconfigs.config3")
	@Import(UserConfigsConfiguration.class)
	private static class TestConfig2 {
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
