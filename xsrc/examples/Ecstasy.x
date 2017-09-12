module Ecstasy.xtclang.org
    {
    typedef Tuple<> Void;

    class Object
        {
        }

    class Int64
        {
        }

    class String
        {
        }

    interface Type {}

    enum Nullable{Null}
    enum Boolean{False, True}

    interface Iterator<ElementType>
        {
        conditional ElementType next();
        }

    package annotations
        {
        mixin InjectedRef {}
        }

    package collections
        {
        interface Tuple // <ElementTypes extends Tuple<ElementTypes...>>
            {
            }

        interface Map<KeyType, ValueType>
            {
            conditional ValueType get(KeyType key);

            Void put(KeyType key, ValueType value);
            }
        }
    }