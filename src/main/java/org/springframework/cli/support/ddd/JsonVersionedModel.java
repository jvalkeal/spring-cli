package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.annotation.JacksonAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies model versioning details.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface JsonVersionedModel {

	/**
	 * @return the current version of the model.
	 */
	String currentVersion();

	/**
	 * @return the default version to convert the model to during serialization when no field or getter method is
	 *         annotated with {@link JsonSerializeToVersion}; if not set, the current version is used
	 */
	String defaultSerializeToVersion() default "";

	/**
	 * @return the default version to convert the model to during deserialization when no model version property is
	 *         present; if not set, an exception is thrown when the model version property is missing
	 */
	String defaultDeserializeToVersion() default "";

	/**
	 * @return class of the converter to use when resolving versioning to the current version; not specifying will cause
	 *         models to not be converted at all
	 */
	Class<? extends VersionedModelConverter> toCurrentConverterClass() default VersionedModelConverter.class;

	/**
	 * @return class of the converter to use when resolving versioning to a past version; not specifying will cause
	 *         models to be serialized as the current version
	 */
	Class<? extends VersionedModelConverter> toPastConverterClass() default VersionedModelConverter.class;

	/**
	 * @return whether to always send model data to converters, even when the data is the same version as the version to
	 *         convert to
	 */
	boolean alwaysConvert() default false;

	/**
	 * @return name of property in which the model's version is stored in JSON
	 */
	String propertyName() default "modelVersion";

	/**
	 * @return a version for when the model version property should not be serialized; if not set, the version property
	 *         is always serialized
	 */
	String versionToSuppressPropertySerialization() default "";
}
