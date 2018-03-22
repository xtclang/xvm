module Ecstasy.xtclang.org
    {
    const Module {}
    const Package {}
    const Class {}
    interface Const {}

    package types
        {
        interface Property {}
        interface Method {}
        }

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
            TODO
            }

        static Boolean equals(Object o1, Object o2)
            {
            TODO
            }

        String to<String>()
            {
            TODO
            }

        Object[] to<Object[]>()
            {
            TODO
            }

        Tuple<Object> to<Tuple<Object>>()
            {
            TODO
            }

//        @Auto function Object() to<function Object()>()
//            {
//            TODO
//            }
        }

    interface Enum
        {
        @RO Enumeration<Enum> enumeration;
        @RO Int ordinal;
        @RO String name;
        conditional Enum next();
        conditional Enum prev();
        }

    mixin Enumeration<EnumType extends Enum>
            into Class
        {
        String name;

        @Lazy Int count;
        @Lazy String[] names;
        @Lazy EnumType[] values;
        @Lazy Map<String, EnumType> byName;
        }

    interface Function<ParamTypes, ReturnTypes> // TODO <ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>
        {
//         @Override
//         function Function() to<function Function()>();
        }

    class IntLiteral
        {
        @Auto Int to<Int>()
            {
            TODO
            }
        }

    class Int64
        {
        @Op Int64 add(Int64 n);
        }

    class String
        {
        }

    const Type<DataType> {}

    enum Nullable{Null}
    enum Boolean{False, True}

    interface Iterator<ElementType>
        {
        conditional ElementType next();
        }

    package collections
        {
        interface Sequence<ElementType> {}
        class Array<ElementType> {}

        interface Tuple<ElementTypes extends Tuple<ElementTypes...>>
            {
            }

        interface Map<KeyType, ValueType>
            {
            conditional ValueType get(KeyType key);

            Void put(KeyType key, ValueType value);
            }
        }

    interface Referent
        {
        @RO Type ActualType;
        <AsType> AsType maskAs<AsType>();
        <AsType> conditional AsType revealAs<AsType>();
        Boolean instanceOf(Type type);
        Boolean implements_(Class interface_);
        Boolean extends_(Class class_);
        Boolean incorporates_(Class mixin_);
        @RO Boolean service_;
        @RO Boolean const_;
        @RO Boolean immutable_;
        }

    interface Ref<RefType>
            extends Referent
        {
        @RO Boolean assigned;
        conditional RefType peek()
            {
            if (assigned)
                {
                return True, get();
                }

            return False;
            }
        RefType get();
        @Override
        @RO Type ActualType;
        static Boolean equals(Ref value1, Ref value2)
            {
            return value1 == value2;
            }
        @RO String? name;
        @RO Int byteLength;
        @RO Boolean selfContained;
        }

    interface Var<RefType>
            extends Ref<RefType>
        {
        Void set(RefType value);
        }

    const Exception(String? text, Exception? cause = null)
        {
        }
    const UnsupportedOperationException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        construct UnsupportedOperationException(String? text, Exception? cause) {} // TODO bug that this is necessary
        }

    package annotations
        {
        mixin AutoConversion into Method {}
        mixin ReadOnly into Property {}
        mixin Operator(String? token = null) into Method {}
        mixin Override into Property | Method {}
        mixin InjectedRef<RefType> into Ref<RefType> {}
        mixin UncheckedInt into Int64 {}
        mixin LazyVar<RefType>(function RefType ()? calculate)
                into Var<RefType>
            {
            private function RefType ()? calculate;
            private Boolean assignable = false;

            RefType get()
                {
                TODO
//                if (!assigned)
//                    {
//                    RefType value = calculate == null ? calc() : calculate();
//                    try
//                        {
//                        assignable = true;
//                        set(value);
//                        }
//                    finally
//                        {
//                        assignable = false;
//                        }
//
//                    return value;
//                    }
//
//                return super();
                }

            Void set(RefType value)
                {
                TODO
//                assert !assigned && assignable;
//                super(value);
                }

            protected RefType calc()
                {
                TODO construct LazyVar with a calculate function, or override the calc() method
                }
            }
        }
    }