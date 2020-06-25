/**
 * An UnassignedVar annotation is used to indicate that the declared variable may be purposefully
 * unassigned.
 *
 * This is a compile-time-only annotation. Its purpose is to prevent a compiler error that would
 * otherwise occur when a property or variable is not explicitly assigned.
 */
mixin UnassignedVar<Referent>
        into Var<Referent>
    {
    }
