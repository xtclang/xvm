/**
 * A TypeParameter is a "generic-type parameter" for a Class or Type.
 *
 * For example, the following declares an interface with two type parameters "Key" and
 * "Value":
 *
 *   interface Map<Key, Value> {...}
 *
 * And the following declares a class that narrows those type parameters:
 *
 *   class HashMap<Key extends Hashable, Value>
 *           implements Map<Key, Value> {...}
 */
const TypeParameter(String name, TypeTemplate type)
    {
    /**
     * Construct a type parameter by narrowing an existing type parameter.
     */
    construct(TypeParameter param, TypeTemplate type)
        {
        // this type parameter has to be a narrower type than the template that it is based on
        assert param.type.isA(type);

        this.name = param.name;
        this.type = type;
        }
    }