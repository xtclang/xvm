/**
 * An FinalVar annotation is used to indicate that the declared variable is not permitted to be
 * modified after it has been assigned.
 *
 * This is both a compile-time and a run-time annotation. Its purpose is to produce a compiler error
 * when a variable is potentially assigned more than once, and to produce a runtime exception when
 * an attempt to assign to a variable occurs after the variable has already been assigned.
 */
mixin FinalVar<Referent>
        into Var<Referent> {

    @Override
    void set(Referent value) {
        assert !assigned;
        super(value);
    }
}
