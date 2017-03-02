TODO need a growable-from-empty array a-la arraylist (maybe with max and/or init capacity?)
TODO need a fixed size array that inits each element based on a value or fn()
TODO need a const array
TODO need that interface that allows things to be made const / made persistent / cloned to make mutable ...

/**
 * An array is a container of elements of a particular type.
 */
class Array<ElementType>
        implements Sequence<ElementType>
    {
    construct Array(Int capacity)
        {
        if (capacity < 0)
            {
            throw new IllegalArgument("capacity", capacity, "must be >= 0");
            }
        this.capacity = capacity;
        }

    construct Array(Int capacity, function ElementType(Int) supply)
        {
        construct Array(capacity);

        Element<ElementType>? head = null;
        if (capacity > 0)
            {
            head = new Element<ElementType>(supply(0));

            Element<ElementType> tail = head;
            for (Int i : 1..capacity)
                {
                Element<ElementType> node = new Element<>(supply(i));
                tail.next = node;
                tail      = node;
                }
            }

        this.head     = head;
        this.capacity = capacity;
        this.length   = capacity;
        }

    public/private Int capacity = 0;
    public/private Int size     = 0;

    @override
    Ref<ElementType> elementAt(Int index)
        {
        if (index < 0 || index >= length)
            {
            throw new IllegalArrayIndex(index, 0, length);
            }

        Element element = (Element) head;
        while (index-- > 0)
            {
            element = (Element) element.next;
            }

        return element;
        }

    private Element<ElementType>? head;

    class Element<RefType>(ElementType value)
            extends Ref<RefType>
        {
        ElementType get()
            {
            return value;
            }

        void set(ElementType value)
            {
            this.value = value;
            }

        Element<RefType>? next;
        }


    @Override
    Sequence<ElementType> ensureMutable();

    /**
     * Return a fixed-size tuple (whose values are mutable) of the same type and with the same
     * contents as this tuple. If this tuple is already a fixed-size tuple, then _this_ is returned.
     */
    @Override
    Sequence<ElementType> ensureFixedSize();

    /**
     * Return a persistent tuple of the same element types and values as are present in this tuple.
     * If this tuple is already persistent or {@code const}, then _this_ is returned.
     *
     * A _persistent_ tuple does not support replacing the contents of the elements in this tuple
     * using the {@link replace} method; instead, calls to {@link replace} will return a new tuple.
     */
    @Override
    Sequence<ElementType> ensurePersistent();

    /**
     * Return a {@code const} array of the same type and contents as this array.
     *
     * All mutating calls to a {@code const} array will result in the creation of a new
     * {@code const} array with the requested changes incorporated.
     *
     * @throws Exception if any of the values in the array are not {@code const} and are not
     *         {@link ConstAble}
     */
    @Override
    Array<ElementType> ensureConst();
    }
