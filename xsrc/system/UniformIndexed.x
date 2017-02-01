/**
 * UniformIndexed is an interface that allows the square bracket operators
 * to be used with a container that contains elements of a specified type,
 * indexed by a specified type.
 */
interface UniformIndexed<IndexType, ElementType>
    {
    /**
     * Obtain the value of the specified element.
     */
    ElementType get(IndexType index);

    /**
     * Modify the value in the specified element.
     */
    Void set(IndexType index, ElementType newValue)
        {
        throw new TODO
        }

    /**
     * Obtain a Ref for the specified element.
     */
    Ref<ElementType> elementAt(IndexType index)
        {
        return new TODO
        }
    }
