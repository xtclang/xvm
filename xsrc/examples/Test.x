module Test
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
