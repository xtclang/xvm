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
        // TODO conditional incorporation of something to do comparable?
        // TODO have to implement Const (at least conditionally if ElementType extends Const)
        // TODO fill()
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

            if (size > 1)
                {
                for (Int i : 1..size-1)
                    {
                    Element next = new Element(valueFor(i));
                    cur.next = next;
                    cur      = next;
                    }
                }
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
                    ? e -> (e.is(Const) ? e : e.is(ConstAble) ? e.ensureConst() : assert)
                    : e -> e;

            Int     index = size - 1;
            Element cur   = new Element(transform(elements[index]));
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

    /**
     * Construct a slice of another array.
     *
     * @param array    the array that this slice delegates to (which could itself be a slice)
     * @param section  the range that defines the section of the array that the slice represents
     */
    construct(Array<ElementType> array, Range<Int> section)
        {
        this.delegate = array;
        this.section  = section;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The capacity of an array is the amount that the array can hold without resizing.
     */
    Int capacity
        {
        @Override
        Int get()
            {
            if (delegate == null)
                {
                Int count = 0;
                for (Element? cur = head; cur != null; cur = cur.next)
                    {
                    ++count;
                    }
                return count;
                }
            else
                {
                return section.as(Range<Int>).size;
                }
            }

        @Override
        void set(Int newCap)
            {
            assert delegate == null;

            Int oldCap = capacity; // TODO GG should be "get()" instead, but it can't find it
            if (newCap == oldCap)
                {
                return;
                }

            assert newCap >= 0;
            assert newCap >= size;
            assert mutability == Mutable;

            Element cur = new Element();
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
                tail?.next = cur;
                }
            }
        }


    // ----- VariablyMutable interface -------------------------------------------------------------

    @Override
    public/private Mutability mutability.get()
        {
        Array<ElementType>? delegate = this.delegate;
        if (delegate != null)
            {
            Mutability mutability = delegate.mutability;
            return mutability == Mutable ? Fixed : mutability;
            }

        return super();
        }

    @Override
    Array ensureMutable()
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
    Array ensureFixedSize(Boolean inPlace = False)
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
    Array ensurePersistent(Boolean inPlace = False)
        {
        if (delegate == null && inPlace && !mutability.persistent || mutability == Persistent)
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
    immutable Array ensureConst(Boolean inPlace = False)
        {
        if (mutability == Constant)
            {
            return this.as(immutable ElementType[]);
            }

        if (!inPlace || delegate != null)
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
                    assert element.is(ConstAble);
                    this[loop2.count] = element.ensureConst(True);
                    }
                }
            }

        // the "mutability" property has to be set before calling makeImmutable(), since no changes
        // will be possible afterwards
        Mutability prev = mutability;
        if (!meta.immutable_)
            {
            mutability = Constant;
            }

        try
            {
            return makeImmutable();
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


    // ----- UniformIndexed interface --------------------------------------------------------------

    @Override
    @Op("[]")
    ElementType getElement(Int index)
        {
        return elementAt(index).get();
        }

    @Override
    @Op("[]=")
    void setElement(Int index, ElementType value)
        {
        if (mutability.persistent)
            {
            throw new ReadOnly();
            }
        elementAt(index).set(value);
        }

    @Override
    Var<ElementType> elementAt(Int index)
        {
        // TODO make sure that persistent arrays do no expose a read/write element (throw ReadOnly from set())
        Array<ElementType>? delegate = this.delegate;
        if (delegate != null)
            {
            return delegate.elementAt(translateIndex(index));
            }

        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        Element element = head.as(Element);
        while (index-- > 0)
            {
            element = element.next.as(Element);
            }

        return element;
        }


    // ----- Sequence interface --------------------------------------------------------------------

    @Override
    Int size.get()
        {
        Range<Int>? section = this.section;
        if (section != null)
            {
            return section.size;
            }

        Int count = 0;
        Element? cur = head;
        while (cur?.valueRef.assigned)
            {
            ++count;
            }
        return count;
        }

    @Override
    @Op("[..]")
    Array slice(Range<Int> range)
        {
        Array<ElementType> result = new Array(this, range);

        // a slice of an immutable array is also immutable
        return meta.immutable_
                ? result.makeImmutable()
                : result;
        }

    @Override
    Array reify()
        {
        return delegate == null
                ? this
                : new Array(mutability, this);
        }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    Boolean contains(ElementType value)
        {
        // use the default implementation from the Sequence interface
        return indexOf(value);
        }

    @Override
    ElementType[] to<ElementType[]>(VariablyMutable.Mutability? mutability = Null)
        {
        return mutability == null || mutability == this.mutability
                ? this
                : new Array(mutability, this);  // create a copy of the desired mutability
        }

    @Override
    Stream<ElementType> stream()
        {
        TODO Array Stream implementation
        }

    @Override
    Array clone()
        {
        return mutability.persistent && delegate == null
                ? this                          // a persistent array is its own shallow clone
                : new Array(mutability, this);  // create a copy with this array's mutability
        }


    @Override
    @Op("+")
    Array add(ElementType element)
        {
        switch (mutability)
            {
            case Mutable:
                Element el = new Element(element);
                if (head == null)
                    {
                    head = el;
                    }
                else
                    {
                    tail?.next = el;
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Array result = new Array<ElementType>(size + 1, i -> (i < size ? this[i] : element));
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }

    @Override
    @Op("+")
    Array addAll(Iterable<ElementType> values)
        {
        switch (mutability)
            {
            case Mutable:
                for (ElementType value : values)
                    {
                    add(value);
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Iterator<ElementType> iter = values.iterator();
                function ElementType (Int) supply = i ->
                    {
                    if (i < size)
                        {
                        return this[i];
                        }
                    assert ElementType value : iter.next();
                    return value;
                    };
                ElementType[] result = new Array<ElementType>(this.size + values.size, supply);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }

    @Override
    (Array, Int) removeIf(function Boolean (ElementType) shouldRemove)
        {
        Int[]? indexes = null;
        loop: for (ElementType value : this)
            {
            if (shouldRemove(value))
                {
                indexes = (indexes ?: new Int[]) + loop.count;
                }
            }

        if (indexes == null)
            {
            return this, 0;
            }

        if (indexes.size == 1)
            {
            return delete(indexes[0]), 1;
            }

        // copy everything except the "shouldRemove" elements to a new array
        Int                newSize = size - indexes.size;
        Array<ElementType> result  = new Array(newSize);
        Int                delete  = indexes[0];
        Int                next    = 1;
        for (Int index = 0; index < size; ++index)
            {
            if (index == delete)
                {
                delete = next < indexes.size ? indexes[next++] : Int.maxvalue;
                }
            else
                {
                result += this[index];
                }
            }

        return switch (mutability)
            {
            case Mutable   : result;
            case Fixed     : result.ensureFixedSize (True);
            case Persistent: result.ensurePersistent(True);
            case Constant  : result.ensureConst     (True);
            }, indexes.size;
        }

    @Override
    Array clear()
        {
        if (empty)
            {
            return this;
            }

        switch (mutability)
            {
            case Mutable:
                head = Null;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                return new Array<ElementType>(mutability, []);
            }
        }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    Array replace(Int index, ElementType value)
        {
        if (mutability.persistent)
            {
            ElementType[] result = new Array(size, i -> (i == index ? value : this[i]));
            return mutability == Persistent
                    ? result.ensurePersistent(true)
                    : result.ensureConst(true);
            }
        else
            {
            this[index] = value;
            return this;
            }
        }

    @Override
    Array insert(Int index, ElementType value)
        {
        if (index == size)
            {
            return this + value;
            }

        switch (mutability)
            {
            case Mutable:
                Element node = elementAt(index).as(Element);
                node.next  = new Element(node.value, node.next);
                node.value = value;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                ElementType[] result = new Array(size + 1,
                        i -> switch (i <=> index)
                            {
                            case Lesser : this[i];
                            case Equal  : value;
                            case Greater: this[i-1];
                            });
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }

    @Override
    Array insertAll(Int index, Iterable<ElementType> values)
        {
        if (values.size == 0)
            {
            return this;
            }

        if (values.size == 1)
            {
            assert ElementType value : values.iterator().next();
            return insert(index, value);
            }

        if (index == size)
            {
            return this + values;
            }

        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        switch (mutability)
            {
            case Mutable:
                Iterator<ElementType> iter = values.iterator();
                assert ElementType value : iter.next();
                Element  first = new Element(value);
                Element  last  = first;
                Element? head  = this.head;
                while (value : iter.next())
                    {
                    last.next = new Element(value);
                    }
                if (index == 0)
                    {
                    if (head == null)
                        {
                        head = first;
                        }
                    else
                        {
                        last.next = head.next;
                        head      = first;
                        }
                    }
                else
                    {
                    Element node = elementAt(index-1).as(Element);
                    last.next = node.next;
                    node.next = first;
                    }
                this.head = head;
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Iterator<ElementType> iter  = values.iterator();
                Int                   wedge = values.size;
                function ElementType (Int) supply = i ->
                    {
                    if (i < index)
                        {
                        return this[i];
                        }
                    else if (i < index + wedge)
                        {
                        assert ElementType value : iter.next();
                        return value;
                        }
                    else
                        {
                        return this[i-wedge];
                        }
                    };
                ElementType[] result = new Array<ElementType>(this.size + values.size, supply);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }

    @Override
    Array delete(Int index)
        {
        if (index < 0 || index >= size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + size);
            }

        switch (mutability)
            {
            case Mutable:
                if (index == 0)
                    {
                    head = head.as(Element).next;
                    }
                else
                    {
                    Element node = elementAt(index-1).as(Element);
                    node.next = node.next.as(Element).next;
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                ElementType[] result = new Array(size, i -> this[i < index ? i : i+1]);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }

    @Override
    Array delete(Range<Int> range)
        {
        Int lo = range.lowerBound;
        Int hi = range.upperBound;
        if (lo < 0 || hi >= size)
            {
            throw new OutOfBounds("range=" + range + ", size=" + size);
            }

        if (lo == hi)
            {
            return delete(lo);
            }

        if (lo == 0 && hi == size-1)
            {
            return clear();
            }

        switch (mutability)
            {
            case Mutable:
                if (lo == 0)
                    {
                    head = elementAt(hi+1).as(Element);
                    }
                else
                    {
                    elementAt(lo-1).as(Element).next = (hi == size-1)
                            ? null
                            : elementAt(hi+1).as(Element);
                    }
                return this;

            case Fixed:
                throw new ReadOnly();

            case Persistent:
            case Constant:
                Int           gap    = range.size;
                ElementType[] result = new Array(size, i -> this[i < lo ? i : i+gap]);
                return mutability == Persistent
                        ? result.ensurePersistent(true)
                        : result.ensureConst(true);
            }
        }


    // ----- internal implementation details -------------------------------------------------------

    /**
     * Linked list head.
     */
    private Element? head;

    /**
     * Linked list tail.
     */
    private Element? tail.get()
        {
        Element? head = this.head;
        if (head == Null)
            {
            return Null;
            }

        Element cur = head;
        while (True)
            {
            Element? next = cur.next;
            if (next == Null)
                {
                return cur;
                }
            cur = next;
            }
        }

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

    /**
     * When an array is a slice of another array, it delegates operations to that array.
     */
    private Array<ElementType>? delegate;

    /**
     * When an array is a slice of another array, it knows the range of elements (including a
     * potential for reverse ordering) from that array that it represents.
     */
    private Range<Int>? section;

    /**
     * Translate from an index into a slice, into an index into the underlying array.
     *
     * @param index  the index into the slice
     *
     * @return the corresponding index into the underlying array
     */
    private Int translateIndex(Int index)
        {
        Range<Int>? section = this.section;
        assert delegate != null && section != null;

        if (index < 0 || index >= section.size)
            {
            throw new OutOfBounds("index=" + index + ", size=" + section.size);
            }

        return section.reversed
                ? section.upperBound - index
                : section.lowerBound + index;
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
            capacity += 10 * size;
            }

        return capacity;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('[');

        if (ElementType.is(Type<Stringable>))
            {
            loop: for (ElementType v : this)
                {
                if (!loop.first)
                    {
                    appender.add(", ");
                    }

                v.appendTo(appender);
                }
            }
        else
            {
            loop: for (ElementType v : this)
                {
                if (!loop.first)
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
    }
