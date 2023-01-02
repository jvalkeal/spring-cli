package org.springframework.cli.support.xxx;

/**
 * {@code SettingsService} is an interface providing service storing settings
 * using plain {@code POJO} model.
 *
 * Concept of a {@code space} is used as a distinction of a {@code POJO} types
 * so that same {@code POJO} can be stored in a different {@code space}. This
 * means that only one instance of a give class can be stored in a same
 * {@code space}.
 *
 * {@code space} type is {@code String} which is used for identification.
 * If {@code space} type is {@code null} it means a default space which is
 * left for implementation how it is persisted.
 *
 * How settings are persisted is left for the implementation.
 *
 * @author Janne Valkealahti
 */
public interface SettingsService {

	<T> T read(Class<T> type);

	<T> T read(Class<T> type, String space);

	<T> SpaceResult<T> readResult(Class<T> type, String space, int version);

	void write(Object value);

	void write(Object value, String space);

	<T> SpaceResult<T> writeResult(Object value, String space, Class<T> type, int version);

	SpaceState state(String space);

	public interface SpaceResult<T> {
		SpaceState state();
		int version();
		T value();
	}

	/**
	 * Denotes a state of a space. User of a {@link SettingsService} can use this
	 * info to make a choice if error has been occurred during serialisation or
	 * de-serialisation not to mistakenly override a persisted state and possible
	 * lose current active settings.
	 */
	public static enum SpaceState {

		/**
		 * There is no existing serialisation or de-serialisation, effectively meaning
		 * no write or read has happened.
		 */
		UNKNOWN,

		/**
		 * Normal operational state, last write or read has not caused any errors.
		 */
		STABLE,

		/**
		 * Error state meaning either read or write has caused errors. Space may not be
		 * in sync with persisted store.
		 */
		UNSTABLE
	}

	// public static SettingsService of() {
	// 	return new DefaultSettingsService();
	// }
}
