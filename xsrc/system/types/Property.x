/**
 * A Property represents a property of a particular class or type. A property has a type, a name,
 * and a value. At runtime, a property is itself of type {@code Ref}.
 */
const Property<TargetType, PropertyType>(Method<TargetType, <Ref<PropertyType>>, <>> method)
        implements Const, Constable
    {
    /**
     * The property's name.
     */
    String name.get()
        {
        return method.name;
        }

    // TODO determine if the property is lazy, future, atomic, soft, weak

    // ----- dynamic behavior ----------------------------------------------------------------------

    /**
     * Given an object reference of a type that contains this method, obtain the invocable function
     * that corresponds to this method on that object.
     */
    Ref<PropertyType> of(TargetType target)
        {
        return method.invoke(target, Tuple:());
        }

    /**
     * Given an object reference of a type that contains this property, obtain the value of the
     * property.
     */
    PropertyType get(TargetType target)
        {
        return this.of(target).get();
        }

    /**
     * Given an object reference of a type that contains this property, modify the value of the
     * property.
     */
    Void set(TargetType target, PropertyType value)
        {
        return this.of(target).set(value);
        }
    }
