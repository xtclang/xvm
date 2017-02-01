/**
 * NonUniformIndexed is an interface that allows the square bracket operators
 * to be used with a container that contains a specific number of elements, each
 * of an arbitrary type.
 */
interface NonUniformIndexed<ElementType...>
    {
    /**
     * The number of Elements in the Tuple.
     */
    @ro Int length;

    /**
     * Obtain the value of the specified element.
     */
    ElementType[index] get(Int index);

    /**
     * Modify the value in the specified element.
     */
    void set(Int index, ElementType[index] newValue)
        {
        elementAt(index).set(newValue);
        }

    /**
     * Obtain the Ref for the specified element.
     */
    Ref<ElementType[index]> elementAt(Int index);
    }
