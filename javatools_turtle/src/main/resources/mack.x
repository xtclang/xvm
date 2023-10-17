/**
 * This is the "bottom turtle" module for the Java prototype compiler and runtime. It must be
 * compiled simultaneously with the "ecstasy.xtclang.org" module.
 *
 * It is an error for any type or class from this module to be visible to user code.
 */
module mack.xtclang.org {
    /**
     * This is the Ref implementation that allows us to terminate infinite recursion in the compiler
     * and runtime.
     *
     * "I'm still giggling that your bottom turtle uses duck typing." - Mark Falco
     */
    class NakedRef<Referent> {
        Referent get();
    }
}
