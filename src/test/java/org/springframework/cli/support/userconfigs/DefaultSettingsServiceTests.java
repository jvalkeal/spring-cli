package org.springframework.cli.support.userconfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cli.support.userconfigs.config3.Pojo4;
import org.springframework.cli.support.userconfigs.config3.Pojo5;
import org.springframework.cli.support.userconfigs.pojos1.Pojo10;
import org.springframework.cli.support.userconfigs.pojos1.Pojo11;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

public class DefaultSettingsServiceTests {

	private Path tempDir;
	private DefaultSettingsService settingsService;
	private DefaultConversionService conversionService;
	private DefaultSettingsMigrationService settingsMigrationService;

	@BeforeEach
	void setup(@TempDir Path tempDir) {
		this.tempDir = tempDir;
		conversionService = new DefaultConversionService();
		settingsMigrationService = new DefaultSettingsMigrationService(conversionService);
		settingsService = new DefaultSettingsService("test", "TEST_DIR", settingsMigrationService);
		settingsService.setPathProvider(path -> tempDir);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Map<String, Map<Class<?>, ?>>> getSpaceMappings() {
		return (Map<String, Map<String, Map<Class<?>, ?>>>) ReflectionTestUtils.getField(settingsService, "spaceMappings");
	}

	private Map<Class<?>, ?> getClassMappingInfo(String space, String partition) {
		Map<String, Map<String, Map<Class<?>, ?>>> spaceMappings = getSpaceMappings();
		Map<String, Map<Class<?>, ?>> classMappings = spaceMappings.get(space);
		return classMappings.get(partition);
	}

	private Integer getClassVersion(String space, String partition, Class<?> type) {
		Map<Class<?>, ?> classMappings = getClassMappingInfo(space, partition);
		Object partitionInfo = classMappings.get(type);
		return (Integer) ReflectionTestUtils.getField(partitionInfo, "version");
	}

	private String getClassField(String space, String partition, Class<?> type) {
		Map<Class<?>, ?> classMappings = getClassMappingInfo(space, partition);
		Object partitionInfo = classMappings.get(type);
		return (String) ReflectionTestUtils.getField(partitionInfo, "field");
	}

	@Nested
	class Construction {

		@Test
		void settingsDirectoryNameMustBeSet() {
			assertThrows(IllegalArgumentException.class, () -> {
				new DefaultSettingsService(null, null, null);
			});
			assertThrows(IllegalArgumentException.class, () -> {
				new DefaultSettingsService("", null, null);
			});
		}

	}

	@Nested
	class Registration {

		@Test
		void registerClasses() {
			settingsService.register(Pojo4.class);
			settingsService.register(Pojo5.class);
			assertThat(getSpaceMappings()).hasSize(1);
			assertThat(getClassVersion(SettingsService.DEFAULT_SPACE, "p1", Pojo4.class)).isEqualTo(1);
			assertThat(getClassField(SettingsService.DEFAULT_SPACE, "p1", Pojo4.class)).isEqualTo("version");
			assertThat(getClassVersion(SettingsService.DEFAULT_SPACE, "p1", Pojo5.class)).isEqualTo(2);
			assertThat(getClassField(SettingsService.DEFAULT_SPACE, "p1", Pojo5.class)).isEqualTo("version");
		}

	}

	@Nested
	class Store {

		@Test
		void typeNeedsToBeRegistered() {
			assertThrows(IllegalArgumentException.class, () -> {
				settingsService.read(Object.class);
			});
			assertThrows(IllegalArgumentException.class, () -> {
				settingsService.write(new Object());
			});
		}

		@Test
		void shouldStoreInDefaultSpace() {
			settingsService.register(Pojo4.class);

			Pojo4 write = new Pojo4();
			write.setField1("hi");
			settingsService.write(write);

			assertThat(tempDir.resolve(".config/test/default-space-p1-v1.yml")).exists();

			Pojo4 read = settingsService.read(Pojo4.class);
			assertThat(read).isNotNull();
			assertThat(read.getField1()).isEqualTo("hi");
		}

	}

	@Nested
	class Migration {

		@BeforeEach
		void setup() {
			settingsService.register(Pojo10.class);
			settingsService.register(Pojo11.class);
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
			Files.write(dir.resolve("default-space-pojos1-v1.yml"), v1Yaml.getBytes());
			Pojo11 pojo11 = settingsService.read(Pojo11.class);
			assertThat(tempDir.resolve(".config/test/default-space-pojos1-v2.yml")).doesNotExist();
			assertThat(pojo11).isNotNull();
			assertThat(pojo11.getField2()).isEqualTo("value1");
			settingsService.write(pojo11);
			assertThat(tempDir.resolve(".config/test/default-space-pojos1-v2.yml")).exists();
		}
	}

	// @Test
	// void shouldMigrate() throws IOException {
	// 	// context = new AnnotationConfigApplicationContext(TestConfig2.class, Pojo4ToPojo5Migration.class, TestConfig.class);
	// 	context = new AnnotationConfigApplicationContext(TestConfig2.class, Pojo4ToPojo5Converver.class, TestConfig.class);
	// 	SettingsService userConfigsService = context.getBean(SettingsService.class);

	// 	String v1Yaml =
	// 			"""
	// 			---
	// 			version: 1
	// 			field1: value1
	// 			""";
	// 	Path dir = tempDir.resolve(".config").resolve("test");
	// 	Files.createDirectories(dir);
	// 	Files.write(dir.resolve("default-space-p1"),	v1Yaml.getBytes());

	// 	Pojo5 pojo = userConfigsService.read(Pojo5.class, null, null);
	// 	assertThat(pojo).isNotNull();
	// 	assertThat(pojo.getField2()).isEqualTo("write4");
	// }

	// // @Component
	// // private static class Pojo4ToPojo5Migration implements SettingsMigrator<Pojo4, Pojo5> {

	// // 	@Override
	// // 	public Pojo5 migrate(Pojo4 source) {
	// // 		Pojo5 pojo5 = new Pojo5();
	// // 		pojo5.setField2(source.getField1());
	// // 		return pojo5;
	// // 	}
	// // }

	// @Component
	// private static class Pojo4ToPojo5Converver implements Converter<Pojo4, Pojo5> {

	// 	@Override
	// 	public Pojo5 convert(Pojo4 source) {
	// 		Pojo5 pojo5 = new Pojo5();
	// 		pojo5.setField2(source.getField1());
	// 		return pojo5;
	// 	}

	// }

	// @Test
	// void notYetWrittenShouldReturnNullForRead() {
	// 	service.register(Pojo4.class);
	// 	Pojo4 read = service.read(Pojo4.class);
	// 	assertThat(read).isNull();
	// }

	// @Test
	// void shouldStoreSameTypeInMultipleSpaces() {

	// // 	service.register(Pojo1.class, "space1", null, null, null, null);
	// // 	service.register(Pojo1.class, "space2", null, null, null, null);

	// // 	Pojo1 writeSpace1 = Pojo1.of("hi1");
	// // 	service.write(writeSpace1, "space1");

	// // 	Pojo1 writeSpace2 = Pojo1.of("hi2");
	// // 	service.write(writeSpace2, "space2");

	// // 	Pojo1 readSpace1 = service.read(Pojo1.class, "space1");
	// // 	Pojo1 readSpace2 = service.read(Pojo1.class, "space2");
	// // 	assertThat(writeSpace1).isEqualTo(readSpace1);
	// // 	assertThat(writeSpace2).isEqualTo(readSpace2);
	// // 	assertThat(readSpace1.getField1()).isEqualTo("hi1");
	// // 	assertThat(readSpace2.getField1()).isEqualTo("hi2");
	// }

	// @Test
	// void shouldImportFromAnnotation() {
	// 	context = new AnnotationConfigApplicationContext(TestConfig.class);
	// 	SettingsService settingsService = context.getBean(SettingsService.class);
	// 	Pojo4 write4 = new Pojo4();
	// 	write4.setField1("write4");
	// 	settingsService.write(write4);
	// 	Pojo4 read4 = settingsService.read(Pojo4.class);
	// 	assertThat(write4.getField1()).isEqualTo(read4.getField1());
	// }

	// @EnableSettings(Pojo4.class)
	// @Import(SettingsConfiguration.class)
	// private static class TestConfig {
	// }

	// @Test
	// void shouldImportFromScan() {
	// 	context = new AnnotationConfigApplicationContext(TestConfig2.class);
	// 	SettingsService settingsService = context.getBean(SettingsService.class);

	// 	Pojo4 write4 = new Pojo4();
	// 	write4.setField1("write4");
	// 	settingsService.write(write4);
	// 	Pojo4 read4 = settingsService.read(Pojo4.class);
	// 	assertThat(write4.getField1()).isEqualTo(read4.getField1());

	// 	Pojo5 write5 = new Pojo5();
	// 	write5.setField2("write5");
	// 	settingsService.write(write5);
	// 	Pojo5 read5 = settingsService.read(Pojo5.class);
	// 	assertThat(write5.getField2()).isEqualTo(read5.getField2());

	// }

	// @SettingsScan(basePackages = "org.springframework.cli.support.userconfigs.config3")
	// @Import(SettingsConfiguration.class)
	// private static class TestConfig2 {
	// }

	// private static class SettingsConfiguration {

	// 	@Bean
	// 	public SettingsMigrationService userConfigsMigrationService(ObjectProvider<Converter<?, ?>> converters) {
	// 		DefaultConversionService conversionService = new DefaultConversionService();
	// 		converters.forEach(conversionService::addConverter);
	// 		DefaultSettingsMigrationService service = new DefaultSettingsMigrationService(conversionService);
	// 		return service;
	// 	}

	// 	@Bean
	// 	SettingsService userConfigsService(ObjectProvider<SettingsHolder> userConfigsHolder, SettingsMigrationService migrationService) {
	// 		DefaultSettingsService service = new DefaultSettingsService("test", "TEST_DIR", migrationService);
	// 		userConfigsHolder.stream()
	// 			.flatMap(uch -> uch.getUserConfigClasses().stream())
	// 			.forEach(type -> {
	// 				service.register(type);
	// 			});
	// 		return service;
	// 	}
	// }

}
