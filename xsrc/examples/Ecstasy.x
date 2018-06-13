module Ecstasy.xtclang.org
    {
    interface Module {}
    interface Package {}
    const Class<PublicType, ProtectedType extends PublicType, PrivateType extends ProtectedType, StructType extends Struct>
            incorporates conditional Enumeration<PublicType extends Enum>
        {}
    interface Const {}
    interface Struct {}

    package types
        {
        const Property<TargetType, PropertyType>(Method<TargetType, Tuple<>, Tuple<Ref<PropertyType>>> method);
        const Method<TargetType, ParamTypes extends Tuple, ReturnTypes extends Tuple>;
        }

    typedef Tuple<> Void;

    interface Meta<PublicType, ProtectedType, PrivateType, StructType> {}

    class Object
        {
        @Inject protected Meta<Object:public, Object:protected, Object:private> meta;
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
        @Auto function Object() to<function Object()>()
            {
            TODO
            }
        }

    interface Enum extends Const
        {
        @RO Enumeration<Enum> enumeration;
        @RO Int ordinal;
        @RO String name;
        conditional Enum next();
        conditional Enum prev();
        }

    mixin Enumeration<EnumType extends Enum>
            into Class<EnumType>
        {
        String name;
        Int count;
        String[] names;
        EnumType[] values;
        Map<String, EnumType> byName;
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

    class Int64 default(0)
        {
        @Op Int64 add(Int64 n);
        }

    class String
        {
        @Op("+") String append(Object o)
            {
            TODO
            }
        }

    const Type<DataType>
        {
        // TODO remove (temporary to force both produce and consume of DataType)
        DataType foo(DataType dt) {return dt;}
        }

    enum Nullable default(Null) {Null}
    enum Boolean default(False) {False, True}

    interface Iterable<ElementType>
        {
        Iterator<ElementType> iterator();
        }

    interface Iterator<ElementType>
        {
        conditional ElementType next();
        }

    package collections
        {
        interface UniformIndexed<IndexType, ElementType>
            {
            @Op ElementType getElement(IndexType index);
            @Op void setElement(IndexType index, ElementType value)
                {
                TODO
                }
            }

        interface Sequence<ElementType>
                extends UniformIndexed<Int, ElementType>
                extends Iterable<ElementType>
            {
            @RO Int size;
            @Override Iterator<ElementType> iterator()
                {
                TODO
                }
            }

        interface Collection<ElementType>
                extends Iterable<ElementType>
            {
            @RO Int size;
            @RO Boolean empty.get()
                {
                return size > 0;
                }
            @Override Iterator<ElementType> iterator();
            @Op conditional Collection<ElementType> add(ElementType value)
                {
                TODO element addition is not supported
                }
            @Op("-") conditional Collection<ElementType> remove(ElementType value)
                {
                TODO element removal is not supported
                }
            }

        interface List<ElementType>
                extends Sequence<ElementType>
                extends Collection<ElementType>
            {
            }

        class Array<ElementType>
                implements List<ElementType>
            {
            construct(Int capacity = 0)
                {
                if (capacity < 0)
                    {
                    throw new IllegalArgumentException("capacity " + capacity + " must be >= 0");
                    }
                this.capacity = capacity;
                }

            construct(Int size, function ElementType(Int) supply) // fixed size
                {
                construct Array(size);

                Element<ElementType>? head = null;
                if (size > 0)
                    {
                    head = new Element<ElementType>(supply(0));

                    Element<ElementType> tail = head;
                    for (Int i : 1..size)
                        {
                        Element<ElementType> node = new Element<>(supply(i));
                        tail.next = node;
                        tail      = node;
                        }
                    }

                this.head     = head;
                this.capacity = size;
                this.size     = size;
                }

            public/private Int capacity = 0;

            @Override
            public/private Int size     = 0;

            @Override
            @Op ElementType getElement(Int index)
                {
                return elementAt(index).get();
                }

            @Override
            @Op void setElement(Int index, ElementType value)
                {
                elementAt(index).set();
                }

            // @Override
            Ref<ElementType> elementAt(Int index)
                {
                if (index < 0 || index >= size)
                    {
                    throw new BoundsException("index=" + index + ", size=" + size);
                    }

                Element element = head as Element;
                while (index-- > 0)
                    {
                    element = element.next as Element;
                    }

                return element;
                }

            @Op Array!<ElementType> add(Array!<ElementType> that);
            @Op Array!<ElementType> replace(Int index, ElementType value);

            private class Element(ElementType value, Element? next = null)
                    delegates Ref<ElementType>(valueRef)
                {
                Ref<ElementType> valueRef.get()
                    {
                    return &value;
                    }
                }
            }

        interface Tuple<ElementTypes extends Tuple>
            {
            @RO Int size;
            @Op Object getElement(Int index);
            @Op void setElement(Int index, Object newValue);
            @Op Tuple add(Tuple that);
            }

        interface Map<KeyType, ValueType>
            {
            conditional ValueType get(KeyType key);

            void put(KeyType key, ValueType value);
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
// TODO
//            if (assigned)
//                {
//                return True, get();
//                }
            return False;
            }
        RefType get();
        @Override @RO Type ActualType;
//        static <CompileType extends Ref> Boolean equals(CompileType value1, CompileType value2)
//            {
//            return value1 == value2;
//            }
        @RO String? name;
        @RO Int byteLength;
        @RO Boolean selfContained;
        }

    interface Var<RefType>
            extends Ref<RefType>
        {
        void set(RefType value);
        }

    const Exception(String? text, Exception? cause = null)
        {
        }

    const UnsupportedOperationException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }

    const BoundsException(String? text = null, Exception? cause = null)
            extends Exception(text, cause)
        {
        }

    const IllegalArgumentException(String? text, Exception? cause)
            extends Exception(text, cause)
        {
        }

    package annotations
        {
        mixin AutoConversion into Method {}
        mixin ReadOnly into Property {}
        mixin Operator(String? token = null) into Method {}
        mixin Override into Property | Method {}
        mixin InjectedRef<ResourceType>(String resourceName, Object? opts = null) into Property<Object, ResourceType> | Ref<ResourceType>;
        mixin UncheckedInt into Int64 {}
        mixin AnnotateRef<RefType> into Var<RefType> {}
        mixin AnnotateVar<RefType> into Var<RefType> {}
        mixin LazyVar<RefType>(function RefType ()? calculate)
                into Var<RefType>
            {
            private function RefType ()? calculate;
            private Boolean assignable = false;

            @Override
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

            @Override
            void set(RefType value)
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

    package io
        {
        interface Console
            {
            void print(Object o);
            void println(Object o = "");
            }
        }
    }