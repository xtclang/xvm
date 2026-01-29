package org.xvm.compiler.ast;


/**
 * Interface for AST nodes that can be copied.
 *
 * @param <T> the type of the copyable object
 */
public interface Copyable<T extends Copyable<T>> {
    /**
     * Create a deep copy of this object. Child nodes are recursively copied; other fields are
     * shallow copied (same reference).
     *
     * @return a deep copy of this object
     */
    T copy();
}
