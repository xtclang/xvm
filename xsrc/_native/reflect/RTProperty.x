/**
 * This is the native runtime implementation of Property.
 */
const RTProperty<Target, Referent, Implementation extends Ref<Referent>>
            (String    name,
             Boolean   constant,
             Referent? value,
             Boolean   suppressVar,
             Boolean   formal,
             Boolean   hasField,
             Boolean   injected,
             Boolean   lazy,
             Boolean   atomic,
             Boolean   abstract)
        extends Property<Target, Referent, Implementation>
            (name, constant, value, suppressVar, formal, hasField, injected, lazy, atomic, abstract)
    {
    @Override
    String name;

    @Override
    conditional Referent isConstant();

    @Override
    Boolean readOnly;

    @Override
    Boolean hasUnreachableSetter;

    @Override
    Boolean formal;

    @Override
    Boolean hasField;

    @Override
    Boolean injected;

    @Override
    Boolean lazy;

    @Override
    Boolean atomic;

    @Override
    Boolean abstract;

    @Override
    Implementation of(Target target);

    @Override
    Referent get(Target target);

    @Override
    void set(Target target, Referent value);
    }
