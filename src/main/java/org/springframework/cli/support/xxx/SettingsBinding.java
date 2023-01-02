package org.springframework.cli.support.xxx;

public @interface SettingsBinding {

	String space();

	int version() default SpaceVersioned.DEFAULT_VERSION;
}
