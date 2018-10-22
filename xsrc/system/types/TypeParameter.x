/**
 * A TypeParameter is a "generic-type parameter" for a Class or Type.
 *
 * For example, the following declares an interface with two type parameters "KeyType" and
 * "ValueType":
 *
 *   interface Map<KeyType, ValueType> {...}
 *
 * And the following declares a class that narrows those type parameters:
 *
 *   class HashMap<KeyType extends Hashable, Value>
 *           implements Map<KeyType, ValueType> {...}
 */
const TypeParameter(String name, Type type = Object)
    {
    /**
     * Construct a type parameter by narrowing an existing type parameter.
     */
    construct(TypeParameter param, Type type)
        {
        // this type parameter has to be a narrower type than the template that it is based on
        assert param.type.isA(type);

        this.name = param.name;
        this.type = type;
        }
    }