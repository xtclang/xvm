import collections.ListMap;

import types.Method;
import types.MultiMethod;

/**
 * A Type is an object that represents an Ecstasy data type. The Type class itself is abstract,
 * but it has a number of well-known concrete implementations.
 *
 * At one level, a data type is simply a set of capabilities, and can be thought of as a set of
 * properties and methods. Simplifying further, a property can be thought of as a method that
 * returns a reference; as a result, a type can be thought of as simply a set of methods.
 *
 * A _parameterized type_ is a type that originates from the formal type of a parameterized class. A
 * parameterized type has a number of named _type parameters_, each of which indicates a particular
 * type that is associated with the name. A type parameter whose value is unknown is _unresolved_,
 * and a type that has one or more unresolved type parameters is an _unresolved type_.
 *
 * A type also provides runtime support for relational operators, such as equality ({@code ==}),
 * inequality ({@code !=}), less-than ({@code <}), less-than-or-equal ({@code <=}), greater-than
 * ({@code >}), greater-than-or-equal ({@code >=}), assignability testing ({@code instanceof}), and
 * the ordering operator ({@code <=>}, also known as _the spaceship operator_). Equality (and thus
 * inequality) for a particular type can be determined using the function provided by the {@link
 * compareForEquality} property. In addition to also providing information on equality and
 * inequality, the {@link compareForOrder} property provides a function that supports all of the
 * ordering-related operators. Lastly, the type's {@link isInstance} method  supports the
 * {@code instanceof} relational operator, answering the question of whether or not the specified
 * object is either assignable to (i.e. _assignment-compatible with_) or castable to this type.
 *
 * A type can also represent an option (a selection) of two of more types, as if the type were
 * _"any one of"_ a set of types. Such a type has two specific attributes:
 * # A set of types; and
 * # A set of methods that reflects the intersection of the sets of methods from each of those
 *   types.
 *
 * Unfortunately, Type cannot be declared as a {@code const} because of the potential for circular
 * references. (The property values of a {@code const} are fully known and immutable before the
 * {@code const} object even has a "`this`"; as a result, it is impossible to create circular
 * references using {@code const} classes.)
 */
const Type<DataType>
    {
    // ----- primary state -------------------------------------------------------------------------

    /**
     * Obtain the raw set of all methods on the type. This includes methods that represent
     * properties.
     */
    Method[] allMethods;

    /**
     * A type can be explicitly immutable. An object can only be assigned to an explicitly immutable
     * type if the object is immutable.
     */
    Boolean explicitlyImmutable;

    // ----- calculated properties -----------------------------------------------------------------

    /**
     * The type's methods (all of them, including those that represent properties), by name.
     */
    @Lazy Map<String, MultiMethod> allMethodsByName.calc()
        {
        assert meta.isImmutable;

        ListMap<String, MultiMethod> map = new ListMap();
        for (Method method : allMethods)
            {
            if (MultiMethod multi := map.get(method.name))
                {
                map.put(method.name, multi.add(method));
                }
            else
                {
                map.put(method.name, new MultiMethod([method]));
                }
            }

        return map.ensureConst();
        }

    /**
     * Obtain the set of properties that exist on the type.
     */
    @Lazy Property[] properties.calc()
        {
        assert meta.isImmutable;

        Property[] list = new Property[];
        for (Method method : allMethods)
            {
            Property? property = method.property;
            if (property != null)
                {
                list += property;
                }
            }
        return list;
        }

    @Lazy Map<String, Property> propertiesByName.calc()
        {
        assert meta.isImmutable;

        Map<String, Property> map = new ListMap();
        for (Property prop : properties)
            {
            map.put(prop.name, prop);
            }
        return map;
        }

    /**
     * Obtain the set of methods on the type that are not present to represent a property. These
     * methods are what developers think of as _methods_.
     */
    @Lazy Method[] methods.calc()
        {
        Method[] list = new Method[];
        for (Method method : allMethods)
            {
            if (method.property == null)
                {
                list += method;
                }
            }
        return list;
        }

    @Lazy Map<String, MultiMethod> methodsByName.calc()
        {
        assert meta.isImmutable;

        ListMap<String, MultiMethod> map = new ListMap();
        for (Method method : methods)
            {
            if (MultiMethod multi := map.get(method.name))
                {
                map.put(method.name, multi.add(method));
                }
            else
                {
                map.put(method.name, new MultiMethod([method]));
                }
            }

        return map.ensureConst();
        }

    /**
     * Determine if references and values of the specified type will be _assignable to_ references
     * of this type.
     *
     * let _T1_ and _T2_ be two types  (T2 == this, T1 == that)
     * * let _M1_ be the set of all methods in _T1_ (including those representing properties)
     * * let _M2_ be the set of all methods in _T2_ (including those representing properties)
     * * let _T2_ be a "derivative type" of _T1_ iff
     *   1. _T1_ originates from a Class _C1_
     *   2. _T2_ originates from a Class _C2_
     *   3. _C2_ is a derivative Class of _C1_
     * * if _T1_ and _T2_ are both parameterized types, let "same type parameter" be a type
     *   parameter of _T1_ that also is a type parameter of _T2_ because _T2_ is a derivative type
     *   of _T1_, or _T1_ is a derivative type of _T1_, or both _T1_ and _T2_ are derivative types
     *   of some _T3_.
     *
     * Type _T2_ is assignable to a Type _T1_ iff both of the following hold true:
     * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
     *    true:
     *    1. _m1_ and _m2_ have the same name
     *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
     *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
     *       1. _p1_ is assignable to _p2_
     *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
     *          the following hold true:
     *          1. _p2_ is assignable to _p1_
     *          2. _T1_ produces _p1_
     *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
     *       _m1_ and _r2_ of _m2_, the following holds true:
     *      1. _r2_ is assignable to _r1_
     * 2. if _T1_ is explicitly immutable, then _T2_ must also be explicitly immutable.
     */
    Boolean isA(Type!<> that)
        {
        if (this.as(Object) == that.as(Object))
            {
            return true;
            }

        if (that.explicitlyImmutable && !this.explicitlyImmutable)
            {
            return false;
            }

        if (that.DataType == Object)
            {
            return true;
            }

        // this type must have a matching method for each method of that type
        nextMethod: for (Method m1 : that.allMethods)
            {
            // find the corresponding method on this type
            for (Method m2 : this.allMethodsByName[m1.name]?.methods)
                {
                if (m2.isSubstitutableFor(m1))
                    {
                    continue nextMethod;
                    }
                }

            // no such matching method
            return false;
            }

        return true;
        }

    /**
     * Determine if this type _consumes_ a formal type with the specified name.
     *
     * @see Method.consumesFormalType
     */
    Boolean consumesFormalType(String typeName, Boolean ignoreImmediateProduction = false)
        {
        return methods.iterator().untilAny(
                method -> method.consumesFormalType(typeName, ignoreImmediateProduction));
        }

    /**
     * Determine if this type _produces_ a formal type with the specified name.
     *
     * @see Method.producesFormalType
     */
    Boolean producesFormalType(String typeName)
        {
        return methods.iterator().untilAny(method -> method.producesFormalType(typeName));
        }

    /**
     * Test whether the specified object is an {@code instanceof} this type.
     */
    Boolean isInstance(Object o)
        {
        return &o.actualType.isA(this);
        }

    /**
     * Cast the specified object to this type.
     */
    DataType cast(Object o)
        {
        assert isInstance(o);
        return o.as(DataType);
        }

    // ----- dynamic type manipulation -------------------------------------------------------------

    /**
     * TODO should it be possible to create a new type from the union of two existing types?
     * REVIEW name? this creates a "union type"
     */
    @Op("+")
    Type add(Type!<> that)
        {
        TODO +
        }

    // TODO intersection type "&" op?

    /**
     * TODO should it be possible to explicitly remove things from a type?
     * REVIEW name? this creates a "difference type"
     */
    @Op("-")
    Type sub(Type!<> that)
        {
        TODO -
        }

    // ----- const contract ------------------------------------------------------------------------

    static <CompileType extends Type> Int hashCode(CompileType value)
        {
        TODO hash
        }

    static <CompileType extends Type> Boolean equals(CompileType value1, CompileType value2)
        {
        TODO ==
        }

    static <CompileType extends Type> Ordered compare(CompileType value1, CompileType value2)
        {
        TODO <=>
        }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 0;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add("Methods: not implemented");
        }

    // ----- ConstAble interface -------------------------------------------------------------------

// REVIEW: Cam, this makes no sense since Type is const; did you mean to create an immutable Type?
//
//    @Override
//    immutable Type<DataType> ensureConst()
//        {
//        return this instanceof immutable Object
//                ? this
//                : new Type<DataType>(allMethods, explicitlyImmutable).ensureConst();
//        }
//
//    @Override
//    immutable Type<DataType> ensureConst()
//        {
//        allMethods = allMethods.ensureConst();
//        meta.isImmutable = true;
//        return this;
//        }
    }
