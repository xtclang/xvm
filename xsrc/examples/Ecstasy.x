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

    package annotations
        {
        mixin InjectedRef {}
        }

    package collections
        {
        interface Tuple // <ElementTypes extends Tuple<ElementTypes...>>
            {
            }
    //
    //    interface Iterator<ElementType>
    //        {
    //        conditional ElementType next();
    //        }
    //
    //    class List<ElementType>
    //        {
    //        ElementType first;
    //
    //        Void add(ElementType value);
    //
    //        Iterator<ElementType> iterator();
    //        }
        }
    }