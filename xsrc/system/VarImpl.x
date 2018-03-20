/**
 * An internal Var implementation.
 */
class VarImpl<RefType>
        extends RefImpl<RefType>
        implements Var<RefType>
    {
    @Override
    Void set(RefType value);
    }
