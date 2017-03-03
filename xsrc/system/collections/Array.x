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

    @Override
    @op ElementType get(Int index)
        {
        return elementAt(index).get();
        }

    @Override
    @op Void set(Int index, ElementType value)
        {
        elementAt(index).set();
        }

    @Override
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

    @op Array.Type<ElementType> slice(Range<Int> range);

    Array.Type<ElementType> reify();

    @op Array.Type<ElementType> add(Array.Type<ElementType> that);
    @op Array.Type<ElementType> replace(Int index, ElementTypevalue);

    static Ordered compare(Array value1, Array value2)
        {
        for (Int i = 0; i < )
        }

    // ----- internal implementation details -------------------------------------------------------

    private Element<ElementType>? head;

    private class Element(ElementType value)
            delegates Ref<ElementType>(&value)
        {
        Element<RefType>? next;
        }

    @Override
    Array.Type<ElementType> ensureMutable()
        {
        return
        }

    /**
     * Return a fixed-size tuple (whose values are mutable) of the same type and with the same
     * contents as this tuple. If this tuple is already a fixed-size tuple, then _this_ is returned.
     */
    @Override
    Array.Type<ElementType> ensureFixedSize();

    /**
     * Return a persistent tuple of the same element types and values as are present in this tuple.
     * If this tuple is already persistent or {@code const}, then _this_ is returned.
     *
     * A _persistent_ tuple does not support replacing the contents of the elements in this tuple
     * using the {@link replace} method; instead, calls to {@link replace} will return a new tuple.
     */
    @Override
    Array.Type<ElementType> ensurePersistent();

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
    Const+Array.Type<ElementType> ensureConst();
    }
