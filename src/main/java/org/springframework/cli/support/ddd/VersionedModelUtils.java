package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder;

public class VersionedModelUtils {

	public static BeanPropertyDefinition getSerializeToVersionProperty(BeanDescription beanDescription) throws RuntimeException {
		BeanPropertyDefinition serializeToVersionProperty = null;
		for(BeanPropertyDefinition definition: beanDescription.findProperties()) {

			// merge field and accessor annotations
			if(definition instanceof POJOPropertyBuilder)
				((POJOPropertyBuilder)definition).mergeAnnotations(true);

			AnnotatedMember accessor = definition.getAccessor();
			if(accessor != null && accessor.hasAnnotation(JsonSerializeToVersion.class)) {
				if(serializeToVersionProperty != null)
					throw new RuntimeException("@" + JsonSerializeToVersion.class.getSimpleName() + " must be present on at most one field or method");
				if(accessor.getRawType() != String.class || (definition.getField() == null && !definition.hasGetter()))
					throw new RuntimeException("@" + JsonSerializeToVersion.class.getSimpleName() + " must be on a field or a getter method that returns a String");
				serializeToVersionProperty = definition;
			}
		}

		return serializeToVersionProperty;
	}


	private VersionedModelUtils() {
	}
}
