package org.xvm.runtime.gc;

/**
 * Thrown to indicate that an attempt was made to access an invalid address. This is considered a fatal error.
 */
@SuppressWarnings("serial")
public class SegFault extends Error {
}
