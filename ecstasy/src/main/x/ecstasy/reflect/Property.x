/**
 * A Property represents a property of a particular implementation or type. A property has a type, a
 * name, and a value. At runtime, a property is itself of type `Ref`.
 */
interface Property<Target, Referent, Implementation extends Ref<Referent>>
        extends immutable Const
    {
    /**
     * The name of the property.
     */
    @RO String name;

    /**
     * Determine if the property represents a constant (a `static` property), and if it does, obtain
     * the constant value.
     *
     * @return True iff the property represents a constant
     * @return (conditional) the constant value
     */
    conditional Referent isConstant();

    /**
     * True iff this property does not expose a `set` method.
     */
    @RO Boolean readOnly;

    /**
    * True iff this represents a property that is read/write (a `Var`) at some level, but not at
    * this level.
    */
    @RO Boolean hasUnreachableSetter;

    /**
    * True iff this property represents a formal type for the class of the object containing this
    * property.
    */
    @RO Boolean formal;

    /**
    * True iff this property has storage allocated in the underlying structure of the object
    * containing this property.
    */
    @RO Boolean hasField;

    /**
    * True iff this property is injected via the `@Inject` annotation.
    */
    @RO Boolean injected;

    /**
    * True iff this property is lazily computed.
    */
    @RO Boolean lazy;

    /**
    * True iff this property is an atomic value.
    */
    @RO Boolean atomic;

    /**
    * True iff this property is abstract.
    */
    @RO Boolean abstract;

    /**
    * The property annotations. These are the annotations that apply to the property itself (i.e.
    * they mix into `Property`), such as `@RO`. The order of the annotations in the array is
    * "left-to-right"; so for example an annotated property:
    *     @A1 @A2 List list = ...
    * would produce the `annotations` array holding `A1` at index zero.
    */
    @RO immutable Annotation[] annotations;


    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * If this "same" property exists on another type (such as the `StructType` for the class, or
     * a type from a super-class or sub-class), obtain the corresponding property that targets that
     * type.
     *
     * @param type  the type to search for a corresponding property
     *
     * @return True iff the "same" property exists on the specified type
     * @return (conditional) the corresponding property from the specified type
     */
    <DataType> conditional Property!<DataType> retarget(Type<DataType> type);

    /**
     * Given an object reference of a type that contains this property, obtain the [Ref] or [Var]
     * that corresponds to this property on that object.
     */
    Implementation of(Target target);

    /**
     * Given a [Ref] or [Var], determine if the [Ref] or [Var] corresponds to this property, and if
     * it does, obtain the target object reference that the [Ref] or [Var] is bound to.
     *
     * @return True iff the specified `Ref` is "of" this property
     * @return (conditional) the target object to which the property is bound
     */
    conditional Target isOrigin(Ref ref);

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


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return Referent.estimateStringLength() + 1 + name.size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        Referent.appendTo(buf);
        buf.add(' ');
        return name.appendTo(buf);
        }
    }
