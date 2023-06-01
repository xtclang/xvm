/**
 * A Var represents a potentially mutable reference.
 *
 * There exist a number of scenarios in which a Var is not mutable at runtime, including:
 * * The Var has been made immutable, or is a property on an object that is immutable;
 * * The Var implementation only allows a single assignment, and it has already been assigned;
 * * The Var only allows assignment under specific conditions.
 */
interface Var<Referent>
        extends Ref<Referent> {
    /**
     * Specify the referent for this variable reference. The Var may reject the mutation by throwing
     * an exception.
     */
    void set(Referent value);
}
