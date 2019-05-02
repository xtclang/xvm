/**
 * UniformIndexed is an interface that allows the square bracket operators to be used with a
 * container data type that contains elements of a specified type, indexed by a specified type.
 */
interface UniformIndexed<IndexType, ElementType>
    {
    /**
     * Obtain the value of the specified element.
     */
    @Op("[]")
    ElementType getElement(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    @Op("[]=")
    void setElement(IndexType index, ElementType value)
        {
        throw new ReadOnly();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    Ref<ElementType> elementAt(IndexType index)
        {
        return new SimpleVar(index);

        /**
         * An implementation of Var that delegates all of the complicated Ref responsibilities to
         * the return value from the {@link UniformIndexed.get} method.
         */
        class SimpleVar(IndexType index)
                delegates Var<ElementType>(ref)
            {
            @Override
            Boolean assigned.get()
                {
                return true;
                }

            @Override
            ElementType get()
                {
                return this.UniformIndexed.getElement(index);
                }

            @Override
            void set(ElementType value)
                {
                this.UniformIndexed.setElement(index, value);
                }

            private Var<ElementType> ref.get()
                {
                ElementType value = get();
                return &value;
                }
            }
        }
    }
