/**
 * A BiType is a Type that represents two separate and independent types. A value of a BiType must
 * be assignable to at least one of the two underlying types of the BiType.
 */
const BiType<DataType extends BiType>(Type type1, Type type2)
        extends Type
    {
    construct BiType(Type type1, Type type2)
        {
        explicitlyImmutable = type1.explicitlyImmutable && type2.explicitlyImmutable;
        resolved            = type1.resolved && type2.resolved;
        }

    @lazy Set<Method> allMethods.calc()
        {
        // TODO
        }

    @lazy Map<String, Type> typeParamsByName.calc()
        {
        }

    }