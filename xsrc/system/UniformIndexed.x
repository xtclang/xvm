/**
 * UniformIndexed is an interface that allows the square bracket operators to be used with a
 * container data type that contains elements of a specified type, indexed by a specified type.
 */
interface UniformIndexed<IndexType, ElementType>
    {
    /**
     * Obtain the value of the specified element.
     */
    @op ElementType get(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    @op Void set(IndexType index, ElementType value)
        {
        throw new ReadOnlyException();
        }

    /**
     * Obtain a Ref for the specified element.
     */
    @op Ref<ElementType> elementAt(IndexType index)
        {
        return new SimpleRef<ElementType>();

        /**
         * An implementation of Ref that delegates all of the complicated Ref responsibilities to
         * the return value from the {@link UniformIndexed.get} method.
         */
        protected class SimpleRef<RefType>
                delegates Ref<RefType>(&get())
            {
            Boolean assigned.get()
                {
                return true;
                }

            RefType get()
                {
                return UniformIndexed.this.get(index);
                }

            Void set(RefType value)
                {
                UniformIndexed.this.set(index, value);
                }
            }
        }
    }
