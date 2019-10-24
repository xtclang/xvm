/**
 * An ObscuringVar annotation is used to indicate that the declared variable is purposefully
 * re-using the name of another variable, thus effectively _hiding_ or _obscuring_ that variable.
 * This is a compile-time-only annotation. Using this annotation when a same-named variable is not
 * already declared in an outer scope will generate a compiler error. Failing to use this annotation
 * when there is a same-named local variable in an outer scope will generate a compiler error.
 */
mixin ObscuringVar<Referent>
        into Var<Referent>
    {
    }
