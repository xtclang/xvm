const Type<T>
    {
    Set<Method> methods;

    Map<String, Property> properties;

    Map<String, >

    Boolean isA(Type that)
        {
        if (this == that)
            {
            return true;
            }

        // this type must have a matching method for each method of that type
        this.methods
        that.methods

        // TODO this is wrong
        return this >= that;
        }

    Boolean isInstance(Object o)
        {
        return o&.ActualType.isA(this);
        }

    // TODO enum properties / map name->prop

    // TODO enum methods / map name->multimethod sig->method

    static Boolean equals(Type value1, Type value2)
        {
        return value1 == value2;
        }

// TODO types are not "strictly" comparable, i.e. t1 could be < or = or > t2, but it could also be none of the above
    static Order compare(Type value1, Type value2)
        {
        return value1 == value2;
        }

    Type add(Type that);

    Type sub(Type that);
    }
