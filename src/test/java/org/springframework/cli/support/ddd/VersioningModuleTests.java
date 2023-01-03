package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersioningModuleTests {

	@Test
	void test1() throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper()
		// .registerModule(new VersioningModule())
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		Car car = new Car();
		car.make = "Citroen";
		car.model = "C4";
		car.year = 2010;

		// String value = objectMapper.writeValueAsString(car);
		// assertThat(value).isNotNull();

		// JsonNode jsonNode = objectMapper.valueToTree(car);
		ObjectNode jsonNode = objectMapper.valueToTree(car);
		jsonNode.put("version", 1);
		String value = objectMapper.writeValueAsString(jsonNode);

		JsonNode readValue = objectMapper.readValue(value, JsonNode.class);
		int asInt = readValue.get("version").asInt();
		// JsonNode jsonNode2 = readValue.get("version");
		Car carx = objectMapper.treeToValue(readValue, Car.class);
		assertThat(carx).isNotNull();

	}

	@Test
	void test2() throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()).registerModule(new VersioningModule());
		Car car = new Car();
		car.make = "Citroen";
		car.model = "C4";
		car.year = 2010;

		String value = objectMapper.writeValueAsString(car);
		assertThat(value).isNotNull();
	}

    @JsonVersionedModel(currentVersion = "3")
    static class Car {
        String make;
        String model;
        int year;
		public String getMake() {
			return make;
		}
		public void setMake(String make) {
			this.make = make;
		}
		public String getModel() {
			return model;
		}
		public void setModel(String model) {
			this.model = model;
		}
		public int getYear() {
			return year;
		}
		public void setYear(int year) {
			this.year = year;
		}
    }
}
