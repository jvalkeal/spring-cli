package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

class VersioningBeanDeserializationModifier extends BeanDeserializerModifier {

	// here just to make generics work without warnings
	private static <T> VersionedModelDeserializer<T> createVersioningDeserializer(StdDeserializer<T> deserializer,
			JsonVersionedModel jsonVersionedModel, BeanPropertyDefinition serializeToVersionProperty) {
		return new VersionedModelDeserializer<T>(deserializer, jsonVersionedModel, serializeToVersionProperty);
	}

	@Override
	public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDescription, JsonDeserializer<?> deserializer) {
		if (deserializer instanceof StdDeserializer) {
			JsonVersionedModel jsonVersionedModel = beanDescription.getClassAnnotations().get(JsonVersionedModel.class);
			if (jsonVersionedModel != null) {
				return createVersioningDeserializer((StdDeserializer<?>) deserializer, jsonVersionedModel,
						VersionedModelUtils.getSerializeToVersionProperty(beanDescription));
			}
		}

		return deserializer;
	}
}
