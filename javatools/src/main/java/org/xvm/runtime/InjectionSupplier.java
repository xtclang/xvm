package org.xvm.runtime;


/**
 * Represents a function that creates an injectable resource.
 */
@FunctionalInterface
public interface InjectionSupplier
    {
    /**
     * Obtain an injectable resource.
     *
     * @param frame  the current frame
     * @param hOpts  (optional) the options handle
     *
     * @return a resource handle
     */
    ObjectHandle supply(Frame frame, ObjectHandle hOpts);
    }