/**
 * A Property represents a property of a particular implementation or type. A property has a type, a name,
 * and a value. At runtime, a property is itself of type `Ref`.
 */
const Property<Target, Referent, Implementation extends Ref<Referent>>
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
    {
    /**
     * The name of the property.
     */
    String name;

    /**
     * True iff the property is a constant.
     */
    private Boolean constant;

    /**
     * The constant value of the property iff the property is a constant.
     */
    private Referent? value;

    /**
     * Determine if the property represents a constant (a `static` property), and if it does, obtain
     * the constant value.
     *
     * @return True iff the property represents a constant
     * @return (conditional) the constant value
     */
    conditional Referent isConstant()
        {
        if (constant)
            {
            return True, value.as(Referent);
            }

        return False;
        }

    /**
    * True iff this property does not expose a `set` method.
    */
    Boolean readOnly.get()
        {
        return constant || !Implementation.is(Type<Var<Referent>>);
        }

    /**
     * True iff this property at some base level is actually a `Var`, but that `Var` is unreachable
     * due to insufficient access.
     */
    private Boolean suppressVar;

    /**
    * True iff this represents a property that is read/write (a `Var`) at some level, but not at
    * this level.
    */
    Boolean hasUnreachableSetter.get()
        {
        return readOnly && suppressVar;
        }

    /**
    * True iff this property represents a formal type for the class of the object containing this
    * property.
    */
    Boolean formal;

    /**
    * True iff this property has storage allocated in the underlying structure of the object
    * containing this property.
    */
    Boolean hasField;

    /**
    * True iff this property is injected via the `@Inject` annotation.
    */
    Boolean injected;

    /**
    * True iff this property is lazily computed.
    */
    Boolean lazy;

    /**
    * True iff this property is an atomic value.
    */
    Boolean atomic;

    /**
    * True iff this property is abstract.
    */
    Boolean abstract;


    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * Given an object reference of a type that contains this method, obtain the invocable function
     * that corresponds to this method on that object.
     */
    Implementation of(Target target)
        {
         for (Property!<> property : Target.properties)
            {
            if (property == this)
                {
                return property.as(Property!<Target, Referent, Implementation>).of(target);
                }
            }
        assert;
        }

    /**
     * Given an object reference of a type that contains this property, obtain the value of the
     * property.
     */
    Referent get(Target target)
        {
        return this.of(target).get();
        }

    /**
     * Given an object reference of a type that contains this property, modify the value of the
     * property.
     */
    void set(Target target, Referent value)
        {
        if (readOnly)
            {
            throw new Exception($"Property {name} is read-only");
            }

        this.of(target).as(Var<Referent>).set(value);
        }
    }
