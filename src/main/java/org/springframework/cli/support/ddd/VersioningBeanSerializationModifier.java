package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

class VersioningBeanSerializationModifier extends BeanSerializerModifier {

	// here just to make generics work without warnings
	private static <T> VersionedModelSerializer<T> createVersioningSerializer(StdSerializer<T> serializer,
			JsonVersionedModel jsonVersionedModel, BeanPropertyDefinition serializeToVersionProperty) {
		return new VersionedModelSerializer<T>(serializer, jsonVersionedModel, serializeToVersionProperty);
	}

	@Override
	public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDescription, JsonSerializer<?> serializer) {
		if (serializer instanceof StdSerializer) {
			JsonVersionedModel jsonVersionedModel = beanDescription.getClassAnnotations().get(JsonVersionedModel.class);
			if (jsonVersionedModel != null) {
				return createVersioningSerializer((StdSerializer<?>) serializer, jsonVersionedModel,
						VersionedModelUtils.getSerializeToVersionProperty(beanDescription));
			}
		}

		return serializer;
	}
}
