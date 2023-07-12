/**
 * `Debug` is a compile-time mixin that marks the class, property, or method as being a link-time
 * conditional using the name-condition of `debug`. Items marked with this annotation will be
 * available in a debugging container, but are unlikely to be available if the code is not running
 * in a debugging container. This means that the annotated class, property, or method will **not**
 * be loaded by default, but will be available when the TypeSystem is created in `debug` mode.
 */
mixin Debug
        extends Iff("debug".defined) {
}
