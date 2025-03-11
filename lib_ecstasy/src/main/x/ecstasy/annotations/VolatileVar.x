/**
 * A VolatileVar annotation is used to indicate that a mutable variable should be captured by
 * reference (rather than by value). It's also required if a lambda attempts to mutate a captured
 * variable.
 */
annotation VolatileVar<Referent>
        into Var<Referent> {
}