import ecstasy.annotations.Inject.Options;

/**
 * A test extension for resource lookup used by the test execution engine.
 */
interface ResourceLookupCallback
        extends Extension {
    /**
     * Returns the resource with the given type and name.
     *
     * @param type  the type of the resource
     * @param name  the name of the resource
     * @param opts  the options for the lookup
     *
     * @return `True` iff this callback can provider thr required resource, otherwise `False`
     * @return the requested resource if this callback can provide it
     */
    conditional Object lookup(Type type, String name, Options opts = Null);
}