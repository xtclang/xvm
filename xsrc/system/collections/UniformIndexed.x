/**
 * UniformIndexed is an interface that allows the square bracket operators to be used with a
 * container data type that contains elements of a specified type, indexed by a specified type.
 */
interface UniformIndexed<IndexType, ElementType>
    {
    /**
     * Obtain the value of the specified element.
     */
    @op ElementType getElement(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    @op Void setElement(IndexType index, ElementType value)
        {
        throw new ReadOnlyException();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    @op Ref<ElementType> elementAt(IndexType index)
        {
        return new SimpleRef();

        /**
         * An implementation of Ref that delegates all of the complicated Ref responsibilities to
         * the return value from the {@link UniformIndexed.get} method.
         */
        class SimpleRef
                delegates Ref<ElementType>(ref)
            {
            Boolean assigned.get()
                {
                return true;
                }

            ElementType get()
                {
                return UniformIndexed.this.get(index);
                }

            Void set(ElementType value)
                {
                UniformIndexed.this.set(index, value);
                }

            private Ref<ElementType> ref.get()
                {
                ElementType value = get();
                return &value;
                }
            }
        }
    }
