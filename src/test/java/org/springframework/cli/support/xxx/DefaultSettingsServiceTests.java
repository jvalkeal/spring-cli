package org.springframework.cli.support.xxx;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cli.support.xxx.config1.Pojo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

class DefaultSettingsServiceTests {

	private Path tempDir;

	@BeforeEach
	void setup(@TempDir Path tempDir) {
		this.tempDir = tempDir;
	}

	@Test
	void settingsDirectoryNameMustBeSet() {
		assertThrows(IllegalArgumentException.class, () -> {
			new DefaultSettingsService(null, null, null);
		});
		assertThrows(IllegalArgumentException.class, () -> {
			new DefaultSettingsService("", null, null);
		});
	}

	@Test
	void shouldStoreInDefaultSpace() {
		DefaultSettingsService service = new DefaultSettingsService("test", "TEST_DIR",null);
		service.setPathProvider(path -> tempDir);
		Pojo1 write = Pojo1.of("hi");
		service.write(write);
		Pojo1 read = service.read(Pojo1.class);
		assertThat(write).isEqualTo(read);
	}

	@Test
	void shouldStoreSameTypeInMultipleSpaces() {
		DefaultSettingsService service = new DefaultSettingsService("test", "TEST_DIR",null);
		service.setPathProvider(path -> tempDir);

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

	void shouldMigrateSettingsFromV1ToV2() {
		// DefaultSettingsService service1 = new DefaultSettingsService("test", "TEST_DIR",null);
		// service1.setPathProvider(path -> tempDir);
	}
}
