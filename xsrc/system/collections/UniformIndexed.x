/**
 * UniformIndexed is an interface that allows the square bracket operators to be used with a
 * container data type that contains elements of a specified type, indexed by a specified type.
 */
interface UniformIndexed<IndexType, Element>
    {
    /**
     * Obtain the value of the specified element.
     */
    @Op("[]")
    Element getElement(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    @Op("[]=")
    void setElement(IndexType index, Element value)
        {
        throw new ReadOnly();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    Var<Element> elementAt(IndexType index)
        {
        return new SimpleVar<IndexType, Element>(this, index);

        /**
         * An implementation of Var that delegates all of the complicated Ref responsibilities to
         * the return value from the {@link UniformIndexed.get} method.
         */
        class SimpleVar<IndexType, Element>(UniformIndexed<IndexType, Element> indexed, IndexType index)
                delegates Var<Element>(ref)
            {
            @Override
            Boolean assigned.get()
                {
                return true;
                }

            @Override
            Element get()
                {
                return indexed.getElement(index);
                }

            @Override
            void set(Element value)
                {
                indexed.setElement(index, value);
                }

            private Var<Element> ref.get()
                {
                Element value = get();
                return &value;
                }
            }
        }
    }
