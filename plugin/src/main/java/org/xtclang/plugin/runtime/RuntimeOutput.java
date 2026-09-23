package org.xtclang.plugin.runtime;

/**
 * Request-owned output destination shared across the implementation classloader boundary.
 * Implementations must not be retained after execution; persistent hosts forward output to the
 * current connection, while in-process hosts adapt their build logger.
 */
public interface RuntimeOutput {
    void out(String text);
    void err(String text);
}
