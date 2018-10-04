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
    construct(Int capacity = 0)
        {
        if (capacity < 0)
            {
            throw new IllegalArgumentException("capacity " + capacity + " must be >= 0");
            }
        this.capacity   = capacity;
        this.mutability = Mutable;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     */
    construct(Int size, function ElementType (Int) supply)
        {
        construct Array(size);

        Element<ElementType>? head = null;
        if (size > 0)
            {
            head = new Element(supply(0));

            Element<ElementType> tail = head;
            for (Int i : 1..size)
                {
                Element<ElementType> node = new Element(supply(i));
                tail.next = node;
                tail      = node;
                }
            }

        this.head       = head;
        this.tail       = tail;
        this.capacity   = size;
        this.size       = size;
        this.mutability = FixedSize;
        }

    public/private Int capacity = 0;

    @Override
    public/private Int size     = 0;

    @Override
    public/private MutabilityConstraint mutability;

    @Override
    @Op ElementType getElement(Int index)
        {
        return elementAt(index).get();
        }

    @Override
    @Op void setElement(Int index, ElementType value)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalStateException("Array is constant");

            case Persistent:
                throw new IllegalStateException("Array is persistent; use the \"replace\" API instead");

            default:
                elementAt(index).set(value);
            }
        }

    @Override
    Var<ElementType> elementAt(Int index)
        {
        if (index < 0 || index >= size)
            {
            throw new BoundsException("index=" + index + ", size=" + size);
            }

        Element element = head.as(Element);
        while (index-- > 0)
            {
            element = element.next as Element;
            }

        return element;
        }

    @Override
    @Op Array!<ElementType> slice(Range<Int> range)
        {
        Int from = range.lowerBound;
        Int to   = range.upperBound;

        ElementType[] that = new ElementType[to - from + 1, (i) -> this[from + i]];
        return that;
        }

    @Override
    Array!<ElementType> reify()
        {
        return this;
        }


    @Op("+") Array!<ElementType> addElement(ElementType element)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalStateException("Array is constant");

            case FixedSize:
                throw new IllegalStateException("Array is fixed size");

            case Persistent:
                {
                ElementType[] that = new ElementType[this.size + 1,
                    (i) -> i < this.size ? this[i] : element];
                that.mutability = Persistent;
                return that;
                }

            default:
                {
                Element el = new Element(element);
                if (head == null)
                    {
                    head = el;
                    tail = el;
                    }
                else
                    {
                    tail.next = el;
                    tail = el;
                    }
                return this;
                }
            }
        }

    @Op("+") Array!<ElementType> addElements(Array!<ElementType> that)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalStateException("Array is constant");

            case FixedSize:
                throw new IllegalStateException("Array is fixed size");

            case Persistent:
                {
                ElementType[] that = new ElementType[this.size + that.size,
                    (i) -> i < this.size ? this[i] : that[i-this.size]];
                that.mutability = Persistent;
                return that;
                }

            default:
                for (Int i = 0; i < that.size; i++)
                    {
                    addElement(that[i]);
                    }
                return this;
            }
        }

    @Op Array!<ElementType> replace(Int index, ElementType value)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalStateException("Array is constant");

            case Persistent:
                {
                ElementType[] that = new ElementType[size, (i) -> this[i]];
                that[index] = value;
                that.mutability = Persistent;
                return that;
                }

            default:
                elementAt(index).set(value);
                return this;
            }
        }

    // ----- Collection API ------------------------------------------------------------------------

    @Override
    Iterator<ElementType> iterator()
        {
        super.iterator();
        }

    @Override
    ElementType[] to<ElementType[]>()
        {
        return this;
        }

    @Override
    Stream<ElementType> stream()
        {
        TODO;
        }

    @Override
    Array<ElementType> clone()
        {
        TODO;
        }

    static <CompileType extends Array> Boolean equals(CompileType a1, CompileType a2)
        {
        Int c = a1.size;
        if (c != a2.size)
            {
            return false;
            }

        for (Int i = 0; i < c; ++i)
            {
            if (a1[i] != a2[i])
                {
                return false;
                }
            }

        return true;
        }

    // ----- internal implementation details -------------------------------------------------------

    private Element? head;
    private Element? tail;

    private class Element(ElementType value, Element? next = null)
            delegates Var<ElementType>(valueRef)
        {
        Var<ElementType> valueRef.get()
            {
            return &value;
            }
        }

    @Override
    Array!<ElementType> ensureMutable()
        {
        switch (mutability)
            {
            case Constant:
            case Persistent:
            case FixedSize:
                {
                ElementType[] that = new ElementType[size, (i) -> this[i]];
                that.mutability = Mutable;
                return that;
                }

            default:
                return this;
            }
        }

    /**
     * Return a fixed-size array (whose values are mutable) of the same type and with the same
     * contents as this array. If this array is already a fixed-size array, then _this_ is returned.
     */
    @Override
    Array!<ElementType> ensureFixedSize(Boolean inPlace = false)
        {
        if (inPlace)
            {
            switch (mutability)
                {
                case Constant:
                    throw new IllegalStateException("Array is constant");

                case Persistent:
                case Mutable:
                    mutability = FixedSize;
                    // fall through
                case FixedSize:
                    return this;
                }
            }
        else
            {
            switch (mutability)
                {
                case Constant:
                case Persistent:
                case Mutable:
                    {
                    ElementType[] that = new ElementType[size, (i) -> this[i]];
                    that.mutability = FixedSize;
                    return that;
                    }

                case FixedSize:
                    return this;
                }
            }
        }

    /**
     * Return a persistent array of the same element types and values as are present in this array.
     * If this array is already persistent or {@code const}, then _this_ is returned.
     *
     * A _persistent_ array does not support replacing the contents of the elements in this array
     * using the {@link replace} method; instead, calls to {@link replace} will return a new array.
     */
    @Override
    Array!<ElementType> ensurePersistent(Boolean inPlace = false)
        {
        if (inPlace)
            {
            switch (mutability)
                {
                case Constant:
                    throw new IllegalStateException("Array is constant");

                case FixedSize:
                case Mutable:
                    mutability = Persistent;
                    // fall through
                case Persistent:
                    return this;
                }
            }
        else
            {
            switch (mutability)
                {
                case Constant:
                case FixedSize:
                case Mutable:
                    {
                    ElementType[] that = new ElementType[size, (i) -> this[i]];
                    that.mutability = Persistent;
                    return that;
                    }

                case Persistent:
                    return this;
                }
            }

        }


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
    immutable Array!<ElementType> ensureConst(Boolean inPlace = false)
        {
        if (mutability == Constant)
            {
            return this.as(immutable ElementType[]);
            }

        if (inPlace)
            {
            return makeImmutable();
            }

        // otherwise, create a constant copy of this
        ElementType[] that = new ElementType[size];
        for (Int i : 0..size-1)
            {
            ElementType el = this[i];
            if (el instanceof ConstAble)
                {
                el = el.as(ConstAble) == this.as(ConstAble)
                    ? that.as(ElementType)
                    : el.ensureConst(false).as(ElementType);
                }
            that[i] = el;
            }
        return that.ensureConst(true);
        }

    @Override
    immutable Array<ElementType> makeImmutable()
        {
        // the "mutability" property has to be set before calling super, since no changes will be
        // allowed afterwards
        MutabilityConstraint prev = mutability;
        if (!meta.immutable_)
            {
            mutability = Constant;
            }

        try
            {
            return super.makeImmutable();
            }
        catch (Exception e)
            {
            if (!meta.immutable_)
                {
                mutability = prev;
                }
            throw e;
            }
        }
    }
