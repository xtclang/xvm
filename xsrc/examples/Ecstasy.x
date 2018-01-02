module Ecstasy.xtclang.org
    {
    const Module {}
    const Package {}

    typedef Tuple<> Void;

//    class Object
//        {
//        String to<String>();
//        @Auto function Object() to<function Object()>();
//        }

    interface Meta<PublicType, ProtectedType, PrivateType, StructType> {}
    class Object
        {
        protected Meta<Object:public, Object:protected, Object:private> meta.get()
            {
            // the Meta object is provided by the runtime
            return super();
            }

        static Boolean equals(Object o1, Object o2)
            {
            return &o1 == &o2;
            }

        String to<String>()
            {
            // the Object's rudimentary to<String> shows class information only
            return meta.class_.to<String>();
            }

        Object[] to<Object[]>()
            {
            return {this};
            }

        Tuple<Object> to<Tuple<Object>>()
            {
            return Tuple:(this);
            }

        @Auto function Object() to<function Object()>()
            {
            return () -> this;
            }

        immutable Object to<immutable Object>()
            {
            meta.immutable_ = true;
            return this.as(immutable Object);
            }
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