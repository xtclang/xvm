/**
 * Array is an implementation of List, an Int-indexed container of elements of a particular type.
 *
 * Array implements all four VariablyMutable forms: Mutable, Fixed, Persistent, and Constant.
 * To construct an Array with a specific form of mutability, use the
 * [construct(Mutability, ElementType...)] constructor.
 */
class Array<ElementType>
        implements List<ElementType>
        implements MutableAble, FixedSizeAble, PersistentAble, ConstAble
        implements Stringable
        incorporates Stringer
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a dynamically growing array with the specified initial capacity.
     *
     * @param capacity  the suggested initial capacity; since the Array will grow as necessary, this
     *                  is not required, but specifying it when the expected size of the Array is
     *                  known allows the Array to pre-size itself, which can reduce the inefficiency
     *                  related to resizing
     */
    construct(Int capacity = 0)
        {
        if (capacity < 0)
            {
            throw new IllegalArgument("capacity (" + capacity + ") must be >= 0");
            }

        if (capacity > 0)
            {
            Element cur = new Element();
            tail = cur;

            while (--capacity > 0)
                {
                cur = new Element(cur);
                }
            head = cur;
            }

        this.mutability = Mutable;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     *
     * @param size    the size of the fixed size array
     * @param supply  the value or the supply function for initializing the elements of the array
     */
    construct(Int size, ElementType | function ElementType (Int) supply)
        {
        if (size > 0)
            {
            function ElementType (Int) valueFor = supply.is(ElementType) ? (_) -> supply : supply;

            Element cur = new Element(valueFor(0));
            head = cur;

            for (Int i : 1..size)
                {
                Element next = new Element(valueFor(i));
                cur.next = next;
                cur      = next;
                }
            tail = cur;
            }

        this.mutability = Fixed;
        }

    /**
     * Construct a fixed size array with the specified size and initial value.
     *
     * @param mutability  the mutability setting for the array
     * @param elements    the elements to use to initialize the contents of the array
     */
    construct(Mutability mutability, ElementType... elements)
        {
        Int size = elements.size;
        if (size > 0)
            {
            function ElementType (ElementType) transform = mutability == Constant
                    ? e -> (e.is(Const) ? e : e.is(ConstAble) ? e.ensureConst().as(ElementType) : assert)
                    : e -> e;

            Int     index = size - 1;
            Element cur   = new Element(transform(elements[index]));
            tail = cur;
            while (--index >= 0)
                {
                cur = new Element(transform(elements[index]), cur);
                }
            head = cur;
            }

        this.mutability = mutability;
        }
    finally
        {
        if (mutability == Constant)
            {
            makeImmutable();
            }
        }


    // ----- VariablyMutable methods ---------------------------------------------------------------

    @Override
    Array! ensureMutable()
        {
        return mutability == Mutable
                ? this
                : new Array(Mutable, this);
        }

    /**
     * Return a fixed-size array (whose values are mutable) of the same type and with the same
     * contents as this array. If this array is already a fixed-size array, then _this_ is returned.
     */
    @Override
    Array! ensureFixedSize(Boolean inPlace = False)
        {
        if (inPlace && mutability == Mutable || mutability == Fixed)
            {
            mutability = Fixed;
            return this;
            }

        return new Array(Fixed, this);
        }

    /**
     * Return a persistent array of the same element types and values as are present in this array.
     * If this array is already persistent or {@code const}, then _this_ is returned.
     *
     * A _persistent_ array does not support replacing the contents of the elements in this array
     * using the {@link replace} method; instead, calls to {@link replace} will return a new array.
     */
    @Override
    Array! ensurePersistent(Boolean inPlace = False)
        {
        if (inPlace && !mutability.persistent || mutability == Persistent)
            {
            mutability = Persistent;
            return this;
            }

        return new Array(Persistent, this);
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
    immutable Array! ensureConst(Boolean inPlace = False)
        {
        if (mutability == Constant)
            {
            return this.as(immutable ElementType[]);
            }

        if (!inPlace)
            {
            return new Array(Constant, this).as(immutable ElementType[]);
            }

        // all elements must be Const or Constable
        Boolean convert = False;
        loop: for (ElementType element : this)
            {
            if (!element.is(Const))
                {
                if (element.is(ConstAble))
                    {
                    convert = True;
                    }
                else
                    {
                    throw new ConstantRequired("[" + loop.count + "]");
                    }
                }
            }

        if (convert)
            {
            loop2: for (ElementType element : this) // TODO CP - loop not loop2
                {
                if (!element.is(Const))
                    {
                    // TODO GG - consider compiler automatically adding the last "as"
                    this[loop2.count] = element.as(ConstAble).ensureConst(True).as(ElementType);
                    }
                }
            }

        return makeImmutable();
        }

    @Override
    immutable Array makeImmutable()
        {
        // the "mutability" property has to be set before calling super, since no changes will be
        // allowed afterwards
        Mutability prev = mutability;
        if (!meta.immutable_)
            {
            mutability = Constant;
            }

        try
            {
            return super();
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


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The capacity of an array is the amount that the array can hold without resizing.
     */
    public Int capacity
        {
        @Override
        Int get()
            {
            Int count = 0;
            for (Element? cur = head; cur != null; cur = cur?.next : assert) // TODO GG "? :" should not be necessary
                {
                ++count;
                }
            return count;
            }

        @Override
        void set(Int newCap)
            {
            Int oldCap = capacity; // TODO GG should be "get()" instead, but it can't find it
            if (newCap == oldCap)
                {
                return;
                }

            assert newCap >= 0;
            assert newCap >= size;
            assert mutability == Mutable;

            Element  cur     = new Element();
            Element? oldTail = tail;
            tail = cur;
            while (--capacity > 0)
                {
                cur = new Element(cur);
                }
            if (head == null)
                {
                head = cur;
                }
            else
                {
                oldTail?.next = cur;
                }
            }
        }

    @Override
    Int size.get()
        {
        Int count = 0;
        Element? cur = head;
        while (cur?.valueRef.assigned)
            {
            ++count;
            }
        return count;
        }

    @Override
    public/private Mutability mutability;


    // ----- TODO ----------------------------------------------------------------------------

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
                throw new IllegalState("Array is constant");

            case Persistent:
                throw new IllegalState("Array is persistent; use the \"replace\" API instead");

            default:
                elementAt(index).set(value);
                break;
            }
        }

    @Override
    Var<ElementType> elementAt(Int index)
        {
        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        Element element = head.as(Element);
        while (index-- > 0)
            {
            element = element.next as Element;
            }

        return element;
        }

    @Override
    @Op Array! slice(Range<Int> range)
        {
        Int from = range.lowerBound;
        Int to   = range.upperBound;

        ElementType[] that = range.reversed
            ? new Array<ElementType>(range.size, (i) -> this[to   - i])
            : new Array<ElementType>(range.size, (i) -> this[from + i]);
        return that;
        }

    @Override
    Array! reify()
        {
        return this;
        }

    @Op("+") Array! addElement(ElementType element)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalState("Array is constant");

            case Fixed:
                throw new IllegalState("Array is fixed size");

            case Persistent:
                ElementType[] newArray = new Array<ElementType>(this.size + 1,
                    (i) -> (i < this.size ? this[i] : element));
                newArray.mutability = Persistent;
                return newArray;

            default:
                Element el = new Element(element);
                if (head == null)
                    {
                    head = el;
                    tail = el;
                    }
                else
                    {
                    tail.as(Element).next = el;
                    tail = el;
                    }
                return this;
            }
        }

    @Op("+") Array! addElements(Array! that)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalState("Array is constant");

            case Fixed:
                throw new IllegalState("Array is fixed size");

            case Persistent:
                ElementType[] newArray = new Array<ElementType>(this.size + that.size,
                    (i) -> (i < this.size ? this[i] : that[i-this.size]));
                newArray.mutability = Persistent;
                return newArray;

            default:
                for (Int i = 0; i < that.size; i++)
                    {
                    addElement(that[i]);
                    }
                return this;
            }
        }

    @Op Array! replace(Int index, ElementType value)
        {
        switch (mutability)
            {
            case Constant:
                throw new IllegalState("Array is constant");

            case Persistent:
                ElementType[] that = new Array<ElementType>(size, (i) -> this[i]);
                that[index] = value;
                that.mutability = Persistent;
                return that;

            default:
                elementAt(index).set(value);
                return this;
            }
        }

    // ----- Collection API ------------------------------------------------------------------------

    @Override
    Iterator<ElementType> iterator()
        {
        TODO;
        }

    @Override
    Array to<Array>()
        {
        return this;
        }

    @Override
    Stream<ElementType> stream()
        {
        TODO;
        }

    @Override
    Array clone()
        {
        TODO;
        }

    static <CompileType extends Array> Boolean equals(CompileType a1, CompileType a2)
        {
        Int c = a1.size;
        if (c != a2.size)
            {
            return False;
            }

        for (Int i = 0; i < c; ++i)
            {
            if (a1[i] != a2[i])
                {
                return False;
                }
            }

        return True;
        }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int capacity = 2; // allow for "[]"
        if (ElementType.is(Type<Stringable>))
            {
            for (ElementType v : this)
                {
                capacity += v.estimateStringLength() + 2; // allow for ", "
                }
            }
        else
            {
            for (ElementType v : this)
                {
                if (v.is(Stringable))
                    {
                    capacity += v.estimateStringLength() + 2;
                    }
                else
                    {
                    capacity += 2;
                    }
                }
            }

        return capacity;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('[');

        if (ElementType.is(Type<Stringable>))
            {
            Append:
            for (ElementType v : this)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }

                v.appendTo(appender);
                }
            }
        else
            {
            Append:
            for (ElementType v : this)
                {
                if (!Append.first)
                    {
                    appender.add(", ");
                    }

                if (v.is(Stringable))
                    {
                    v.appendTo(appender);
                    }
                else
                    {
                    v.to<String>().appendTo(appender);
                    }
                }
            }
        appender.add(']');
        }

    // ----- internal implementation details -------------------------------------------------------

    /**
     * Linked list head.
     */
    private Element? head;

    /**
     * Linked list tail.
     */
    private Element? tail;

    /**
     * A node in the linked list.
     */
    private static class Element
            delegates Var<ElementType>(valueRef)
        {
        /**
         * Construct an empty element.
         *
         * @param next   the next element in the array (optional)
         */
        construct(Element? next = null)
            {
            this.next = next;
            }

        /**
         * Construct an initialized element.
         *
         * @param value  the initial value for the element
         * @param next   the next element in the array (optional)
         */
        construct(ElementType value, Element? next = null)
            {
            this.value = value;
            this.next  = next;
            }

        /**
         * The value stored in the element.
         */
        @Unassigned
        ElementType value;

        /**
         * The next element in the linked list.
         */
        Element? next = null;

        /**
         * The reference to the storage for the `value` property.
         */
        Var<ElementType> valueRef.get()
            {
            return &value;
            }
        }
    }
