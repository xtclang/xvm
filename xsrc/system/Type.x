/**
 * A Type is a const object that represents an Ecstasy data type.
 *
 * At one level, a data type is simply a set of capabilities, and can be thought of as a set of
 * properties and methods. Simplifying further, a property can be thought of as a method that
 * returns a reference; as a result, a type can be thought of as simply a set of methods.
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
 * Types are also parameterizable, using . A type without parameters TODO
 *
 * A type can also represent an option (a selection) of two of more types, as if the type were
 * _"any one of"_ a set of types. Such a type has two specific attributes:
 * # A set of types; and
 * # A set of methods that reflects the intersection of the sets of methods from each of those
 *   types.
 */
const Type<DataType>
        implements Comparator<DataType>
    {
    /**
     * Obtain the raw set of all methods on the type. This includes methods that represent
     * properties.
     */
    @ro Set<Method> allMethods;

    /**
     * Obtain the set of methods on the type that are not present to represent a property. These
     * methods are what developers think of as _methods_.
     */
    @lazy Set<Method> methods.calc()
        {
        return allMethods.filter(m -> m.ReturnTypes.size != 1 || !m.ReturnTypes[0].isA(Ref));
        }

    /**
     * Obtain the set of properties that exist on the type.
     */
    @lazy Set<Property> properties.calc()
        {
        Set<Property> set = new ListSet<>();
        // TODO allMethods.filter(m -> m.ReturnTypes.size == 1 && m.ReturnTypes[0].isA(Ref));
        return set;
        }

    @lazy Map<String, MultiMethod> methodsByName.calc()
        {
        Map<String, MultiMethod> map = new ListMap<>();
        // TODO
        return map;
        }

    @lazy Map<String, Property> propertiesByName.calc()
        {
        Map<String, Property> map = new ListMap<>();
        for (Property prop : properties)
            {
            map[prop.name] = prop;
            }
        return map;
        }

    /**
     * Obtain a function that will evaluate two instances of the type for equality. This is used to
     * provide the functionality of the relational operator "{@code ==}" (and thus "{@code !=}").
     *
     * @throws UnsuportedOperationException if the type does not support an equality function
     */
    @Override
    @ro function Boolean (DataType v1, DataType v2) compareForEquality;

    /**
     * Obtain a function that will evaluate two instances of the type for ordering purposes. This is
     * used to provide the functionality of the relational ordering operator "{@code <=>}", also
     * known as _the spaceship operator_, and thus a number of other relational operators such as
     * less-than ({@code <}), less-than-or-equal ({@code <=}), greater-than ({@code >}), and
     * greater-than-or-equal ({@code >=}).
     *
     * @throws UnsuportedOperationException if the type does not support an ordering function
     */
    @Override
    @ro function Ordered (DataType v1, DataType v2) compareForOrder;

    /**
     * Test whether the specified object is an {@code instanceof} this type.
     */
    Boolean isInstance(Object o)
        {
        return o&.ActualType.isA(this);
        }

    /**
     *
     */
    DataType cast(Object o)
        {
        assert isInstance(o);
        return (DataType) o;
        }

    DataType cast(Object o)
        {
        assert isInstance(o);
        return (DataType) o;
        }

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

        /**
         * Dereference a name to obtain a field.
         */
        @op Ref elementFor(String name)
            {
            for (Ref ref : to<Ref[]>())
                {
                if (ref.name? == name)
                    {
                    return ref;
                    }
                }

            throw new Exception("no such field: " + name);
            }

    }
