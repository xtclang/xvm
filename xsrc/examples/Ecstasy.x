module Ecstasy.xtclang.org
    {
    const Module {}
    const Package {}

    typedef Tuple<> Void;

    class Object
        {
        @Auto function Object() to<function Object()>();
        }

    class IntLiteral
        {
        @Auto Int to<Int>();
        }

    class Int64
        {
        @Op Int64 add(Int64 n);
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

    interface Ref<RefType>
        {
        RefType get();
        Void set(RefType value);
        }

    package annotations
        {
        mixin AutoConversion {}

        mixin Operator(String? token = null) {}

        mixin Override {}

        mixin InjectedRef<RefType> into Ref<RefType> {}
        }
    }