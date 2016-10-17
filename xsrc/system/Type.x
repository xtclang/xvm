public value Type<T>
    {
    // TODO enum properties / map name->prop

    // TODO enum methods / map name->multimethod sig->method

    // TODO Order compare(Type)

    // TODO Type add(Type)

    // TODO Type sub(Type)

    /**
     * Given this Type and a reference that includes this type, create and return a new reference that is bound to this
     * exact type.
     *
     * @param o  reference that is >= T
     *
     * @return TODO type is this
     */
    T bind<T>(Object o); // can't implement this in ecstasy code!
    }
