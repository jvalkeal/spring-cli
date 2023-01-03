package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Converter used by {@link JsonVersionedModel} for resolving model versioning.
 */
public interface VersionedModelConverter {

	/**
	 * Returns JSON data in the target version.
	 *
	 * @param modelData          data to be converted
	 * @param modelVersion       version of the data
	 * @param targetModelVersion version of the data to be returned
	 * @param nodeFactory        node factory
	 * @return model data converted to target version
	 */
	ObjectNode convert(ObjectNode modelData, String modelVersion, String targetModelVersion, JsonNodeFactory nodeFactory);
}
