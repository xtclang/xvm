/**
 * An array is a container of elements of a particular type.
 *
 * TODO need a growable-from-empty array a-la ArrayList (maybe with max and/or init capacity?)
 * TODO need a fixed size array that inits each element based on a value or fn()
 * TODO need a const array
 */
class Array<ElementType>
        implements List<ElementType>
        implements MutableAble
        implements FixedSizeAble
        implements PersistentAble
        implements ConstAble
    {
    /**
     * Construct a dynamically growing array with the specified initial capacity.
     */
    construct Array(Int capacity = 0) // dynamic growth with an initial ca
        {
        if (capacity < 0)
            {
            throw new IllegalArgument("capacity", capacity, "must be >= 0");
            }
        this.capacity = capacity;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     */
    construct Array(Int size, function ElementType(Int) supply) // fixed size
        {
        construct Array(size);

        Element<ElementType>? head = null;
        if (size > 0)
            {
            head = new Element<ElementType>(supply(0));

            Element<ElementType> tail = head;
            for (Int i : 1..size)
                {
                Element<ElementType> node = new Element<>(supply(i));
                tail.next = node;
                tail      = node;
                }
            }

        this.head     = head;
        this.capacity = size;
        this.length   = size;
        }

    public/private Int capacity = 0;

    @Override
    public/private Int size     = 0;

    @Override
    @Op ElementType getElement(Int index)
        {
        return elementAt(index).get();
        }

    @Override
    @Op Void setElement(Int index, ElementType value)
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

        Element element = head as Element;
        while (index-- > 0)
            {
            element = element.next as Element;
            }

        return element;
        }

    @Op Array!<ElementType> slice(Range<Int> range);

    Array!<ElementType> reify();

    @Op Array!<ElementType> add(Array!<ElementType> that);
    @Op Array!<ElementType> replace(Int index, ElementType value);

    static <CompileType extends Array> Boolean equals(CompileType a1, CompileType a2)
        {
        if (a1.size != a2.size)
            {
            return false;
            }

        for (CompileType.ElementType v1 : a1, CompileType.ElementType v2 : a2)
            {
            if (v1 != v2)
                {
                return false;
                }
            }

        return true;
        }

    // ----- internal implementation details -------------------------------------------------------

    private Element? head;

    private class Element(ElementType value, Element? next = null)
            delegates Ref<ElementType>(valueRef)
        {
        Ref<ElementType> valueRef.get()
            {
            return &value;
            }
        }

    @Override
    Array!<ElementType> ensureMutable()
        {
        return TODO
        }

    /**
     * Return a fixed-size array (whose values are mutable) of the same type and with the same
     * contents as this array. If this array is already a fixed-size array, then _this_ is returned.
     */
    @Override
    Array!<ElementType> ensureFixedSize(Boolean inPlace = false);

    /**
     * Return a persistent array of the same element types and values as are present in this array.
     * If this array is already persistent or {@code const}, then _this_ is returned.
     *
     * A _persistent_ array does not support replacing the contents of the elements in this array
     * using the {@link replace} method; instead, calls to {@link replace} will return a new array.
     */
    @Override
    Array!<ElementType> ensurePersistent(Boolean inPlace = false);

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
    immutable Array!<ElementType> ensureConst(Boolean inPlace = false);
    }
