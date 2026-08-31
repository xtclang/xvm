package org.xvm.runtime.gc;

/**
 * Thrown to indicate that an attempt was made to access an invalid address. This is considered a fatal error.
 */
public class SegFault extends Error {
    /** Never Java-serialized; present so the serial lint stays clean. */
    private static final long serialVersionUID = 1L;

}
