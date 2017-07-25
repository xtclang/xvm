module Ecstasy.xtclang.org
    {
    class Object
        {
        }

    class Int64
        {
        }

    enum Nullable{Null}
    enum Boolean{False, True}

    package Collections
        {
        interface Iterator<ElementType>
            {
            conditional ElementType next();
            }

        class List<ElementType>
            {
            ElementType first;

            Void add(ElementType value);

            Iterator<ElementType> iterator();
            }
        }
    }