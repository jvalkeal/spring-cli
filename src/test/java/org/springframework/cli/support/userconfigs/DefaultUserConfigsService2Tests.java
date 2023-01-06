package org.springframework.cli.support.userconfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cli.support.userconfigs.config3.Pojo4;
import org.springframework.cli.support.userconfigs.config3.Pojo5;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultUserConfigsService2Tests {

	private Path tempDir;
	private DefaultUserConfigsService2 service;
	private ConfigurableApplicationContext context;

	@BeforeEach
	void setup(@TempDir Path tempDir) {
		this.tempDir = tempDir;
		this.service = new DefaultUserConfigsService2("test", "TEST_DIR");
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

}
