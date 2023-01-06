package org.springframework.cli.support.userconfigs;

import org.springframework.core.NestedRuntimeException;

@SuppressWarnings("serial")
public abstract class ConversionException extends NestedRuntimeException {

	/**
	 * Construct a new conversion exception.
	 * @param message the exception message
	 */
	public ConversionException(String message) {
		super(message);
	}

	/**
	 * Construct a new conversion exception.
	 * @param message the exception message
	 * @param cause the cause
	 */
	public ConversionException(String message, Throwable cause) {
		super(message, cause);
	}

}
