package org.springframework.cli.support.ddd;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class VersioningModule extends SimpleModule {

	public VersioningModule() {
		super("VersioningModule");
		setDeserializerModifier(new VersioningBeanDeserializationModifier());
		setSerializerModifier(new VersioningBeanSerializationModifier());
	}
}
