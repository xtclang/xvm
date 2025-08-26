package org.xvm.util;

/**
 * An {@link AutoCloseable} which does not throw; this is intended to be used as the resource in
 * a try-with-resource statement.
 */
@FunctionalInterface
public interface Auto
    extends AutoCloseable
    {
    @Override
    void close();
    }
