package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.annotation.JacksonAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When used in conjunction with {@link JsonVersionedModel}, this annotation marks a String field or String-returning
 * method whose value will indicate which version to serialize to.
 * <ul>
 * <li>{@link JsonVersionedModel#toPastConverterClass()} must be set for this to be used.</li>
 * <li>There may only be one field or method per class marked with this annotation.</li>
 * <li>When the value of the field or method is null, {@link JsonVersionedModel#defaultSerializeToVersion()} is used.
 * If that is not set, {@link JsonVersionedModel#currentVersion()} is used</li>
 * </ul>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface JsonSerializeToVersion {

	/**
	 * @return whether to set this property to the source model version during deserialization
	 */
	boolean defaultToSource() default false;
}
