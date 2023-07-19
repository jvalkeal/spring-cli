package org.springframework.cli.support.userconfigs.migration;

/**
 * For registering migrators with a type migration system.
 *
 * @author Janne Valkealahti
 */
public interface SettingsMigratorRegistry {

	/**
	 * Add a plain migrator to this registry.
	 * The convertible source/target type pair is derived from the Migrator's parameterized types.
	 * @throws IllegalArgumentException if the parameterized types could not be resolved
	 */
	void addMigrator(SettingsMigrator<?, ?> migrator);

	/**
	 * Add a plain migrator to this registry.
	 * The migratable source/target type pair is specified explicitly.
	 * <p>Allows for a Migrator to be reused for multiple distinct pairs without
	 * having to create a Migrator class for each pair.
	 */
	<S, T> void addMigrator(Class<S> sourceType, Class<T> targetType, SettingsMigrator<? super S, ? extends T> migrator);

	/**
	 * Add a generic converter to this registry.
	 */
	void addMigrator(GenericSettingsMigrator migrator);

}
