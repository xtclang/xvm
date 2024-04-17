import ecstasy.io;


/**
 * Array is **the** fundamental implementation of the List interface; it is an Int-indexed container
 * of elements of a specific type, and it can be assumed to have an `O(1)` element access time, both
 * for element access and modification.
 *
 * The Array class supports four different modes of [Mutability]:
 *
 * * The traditional [Fixed](Mutability.Fixed)-size array whose elements can replaced but whose size
 *   is fixed; element insertion and deletion are not supported.
 *
 * * The variably-sized [Mutable](Mutability.Mutable) array, that grows as elements are appended or
 *   inserted, and shrinks as elements are deleted.
 *
 * * The [Persistent](Mutability.Persistent) array, that is not itself modifiable, but instead
 *   produces a new array representing each requested modification.
 *
 * * The [Constant](Mutability.Constant) array, which behaves like a Persistent array, but
 *   additionally is immutable and contains only immutable elements.
 *
 * In the case of the variably-sized Mutable array, appending and removing from the end of the list
 * can also be assumed to have `O(1)` cost, while insertions and deletions should generally be
 * avoided due to their expected `O(n)` behavior.
 *
 * Some data structures are capable of mutating their data in-place, while others (called
 * "persistent" data structures) create a new copy (or representation) of the data structure when a
 * mutating operation is executed. (The use of the term "persistent" in this context is not related
 * to persistent _storage_, such as in a database or on disk; it refers instead to the "persistence"
 * of the state of the _old_ data structure, even after a change is made that resulted in the
 * creation of a _new_ data structure.) Array supports both in-place mutability and persistent data
 * structure modes, and "freezes" to the persistent mode. There are two in-place mutability options:
 * A fully mutable mode that allows the array to grow and shrink, and a fixed-size mode for
 * optimization purposes. To construct an Array with a specific form of mutability, use the
 * [construct(Mutability, Element[])] constructor.
 *
 * The Array behavior maps directly to computer memory, in that a contiguous block of memory _is_
 * (quite literally) an array of bytes, and one would expect (and generally, one would hope) that
 * an Ecstasy `Byte[]` would map directly to a block of memory. In C and many C-style languages, a
 * block of memory can also be treated as an array of larger elements, such as integers, floating
 * point numbers, structures, and -- perhaps most importantly -- pointers. In Ecstasy, the
 * underlying layout of the array data, including whether the data is stored "by value" (structure)
 * or "by reference" (pointer), is managed by the runtime, and in so doing, can be optimized to
 * achieve various goals such as speed, footprint minimization, cache locality, and so on. One would
 * expect a fixed size `Int[]` to be nearly identical in its implementation to a C array of the same
 * type, and it likely is. What is more complex is the layout of an array of a complex mutable type;
 * most high level languages will always use an array of pointers, thus having `n+1` total memory
 * allocations for an array of `n` elements, and a CPU cache-defeating "pointer chasing" effect on
 * access and iteration. The Ecstasy design allows such an array to still be laid out in the same
 * manner as a C array of structs, **or** as a C array of pointers (or fat pointers). Regardless,
 * the programming model is the same in every case for the Ecstasy programmer, and the range of
 * automatic and potentially dynamic optimizations are similar to those that are available (with
 * manual effort, and without dynamicity) in a low level, statically compiled language.
 */
class Array<Element>
        implements ArrayDelegate<Element>
        implements List<Element>
        implements Freezable
        implements Stringable
        incorporates conditional HashableArray<Element extends Hashable>
        incorporates conditional OrderableArray<Element extends Orderable>
        incorporates conditional arrays.BitArray<Element extends Bit>
        incorporates conditional arrays.ByteArray<Element extends Byte>
        incorporates conditional arrays.NibbleArray<Element extends Nibble>
        incorporates conditional arrays.NumberArray<Element extends Number>
        incorporates conditional arrays.IntNumberArray<Element extends IntNumber>
        incorporates conditional arrays.FPNumberArray<Element extends FPNumber> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a dynamically growing array with the specified initial capacity. This constructor
     * is used by the following syntax:
     *
     *      Int[] numbers = new Int[];
     *      String[] names = new String[](100);    // the (100) is the initial capacity argument
     *
     * The resulting array is [Mutable](Mutability.Mutable), and its size grows dynamically as
     * elements are appended to it:
     *
     *      numbers += 5;
     *      names += ["Eva", "Hadi", "Felix"];
     *
     * @param capacity  the suggested initial capacity; since the Array will grow as necessary, this
     *                  is not required, but specifying it when the expected size of the Array is
     *                  known allows the Array to pre-size itself, which can reduce the inefficiency
     *                  related to resizing
     */
    construct(Int capacity = 0) {
        assert capacity >= 0;
        construct Array(new Element[](capacity), Mutable);
    }

    /**
     * Construct a fixed size array with the specified size and initial value. An initial value is
     * always required
     *
     * @param size    the size of the fixed size array
     * @param supply  the value or the supply function for initializing the elements of the array
     */
    construct(Int size, Element | function Element (Int) supply) {
        construct Array(new Element[size](supply), Fixed);
    }

    /**
     * Construct an array of the specified mutability, and optionally initialized with the specified
     * contents.
     *
     * @param mutability  the mutability setting for the array
     * @param elements    the elements to use to initialize the contents of the array
     */
    construct(Mutability mutability, Iterable<Element> elements = []) {
        ArrayDelegate<Element> delegate;
        Int size = elements.size;
        if (mutability == Mutable) {
            delegate = new Element[](size).addAll(elements);
        } else if (size == 0) {
            delegate = new Element[0](_ -> assert);
        } else {
            Iterator<Element> iter = elements.iterator();
            if (mutability == Constant) {
                iter = iter.map(e -> frozen(e));
            }
            delegate = new Element[elements.size](_ -> iter.take());
            assert !iter.next();
        }

        construct Array(delegate, mutability);
    }

    @Override
    construct(Array that) {
        if (that.inPlace && !that.is(immutable)) {
            // make a copy of the underlying data for the new array
            construct Array(that.mutability, that);
        } else {
            // use the existing underlying data for the new array
            construct Array(that.delegate, that.mutability);
        }
    }

    /**
     * Construct an array that delegates to some other data structure (such as an array).
     *
     * @param delegate  an ArrayDelegate object that allows this array to delegate its functionality
     */
    protected construct(ArrayDelegate<Element> delegate, Mutability mutability) {
        this.delegate   = delegate;
        this.mutability = mutability;
    } finally {
        if (mutability == Constant) {
            makeImmutable();
        }
    }


    // ----- ArrayDelegate -------------------------------------------------------------------------

    /**
     * An interface to which an Array can delegate its operations,l iff the array is simply a
     * representation of some other structure (such as another array).
     */
    private static interface ArrayDelegate<Element>
            extends Duplicable {
        /**
         * The ArrayDelegate behaves in the manner defined by Array [Mutability].
         */
        @RO Mutability mutability;

        /**
         * The capacity of the ArrayDelegate, in terms of the number of elements. This is a
         * read/write value, but an ArrayDelegate may reject (by exception) or ignore a modification
         * if it does not allow the capacity to be modified.
         */
        Int capacity;

        /**
         * The number of elements in the ArrayDelegate.
         */
        @RO Int size;

        /**
         * Obtain a reference to the cell of the array that contains the specified element.
         *
         * @param index  the index of the element
         *
         * @return the Var reference for the element at the specified index
         */
        Var<Element> elementAt(Int index);

        /**
         * Insert the specified element into the ArrayDelegate at the specified index. This method
         * can only be used if the ArrayDelegate is mutable. This operation elides the elements
         * before the specified index with the inserted element and then with the elements that were
         * previously present starting at the specified index, and is almost certainly as
         * horrifyingly expensive as it sounds.
         *
         * @param index  the index at which to insert an element into the ArrayDelegate
         *
         * @return the resulting ArrayDelegate
         */
        ArrayDelegate insert(Int index, Element value);

        /**
         * Delete the specified element from the ArrayDelegate. This can only be used if the
         * ArrayDelegate is mutable. This operation elides the elements before the specified index
         * with the elements after the specified index, and is almost certainly as horrifyingly
         * expensive as it sounds.
         *
         * @param index  the index of the element to delete from the ArrayDelegate
         *
         * @return the resulting ArrayDelegate
         */
        ArrayDelegate delete(Int index);

        /**
         * If the delegate is itself not an actual storage of array elements, then create a new,
         * independent storage for the contents represented by this delegate, and return that new
         * storage.
         *
         * @param mutability  the desired mutability setting for the array delegate
         *
         * @return a reified array delegate that provides storage for the elements that it
         *         represents
         */
        ArrayDelegate! reify(Mutability? mutability = Null);
    }

    /**
     * If the array is simply a representation of some other structure (such as another array), then
     * this is the object to which this Array will delegate its operations.
     */
    private ArrayDelegate<Element> delegate;


    // ----- variable Mutability -------------------------------------------------------------------

    /**
     * The levels of mutability of an array.
     */
    enum Mutability {
        /*
         * A _Constant_ array structure is also a persistent data structure, but additionally
         * it must be immutable, and all of its contents must be immutable.
         */
        Constant,
        /*
         * A _Persistent_ array structure handles mutation requests by creating a new array
         * structure with the requested changes. It is common for multiple persistent array
         * structures to share mutable internal data for efficiency purposes.
         *
         * A persistent data structure is a container data type that creates new versions of itself
         * in response to modification requests, thus avoiding changing its own state. A persistent
         * data structure behaves like an immutable data structure, except that its contained data
         * types are not required to be immutable; it acts as if it were "shallow immutable".
         *
         * For example, a persistent array data structure cannot be increased in size, nor decreased
         * in size, nor can any of its elements have their values replaced (i.e. each element has a
         * referent, and the array will not allow any element to modify which referent it holds).
         *
         * The benefit of a persistent data structure is similar to that of a `const` value, in
         * that it is safe to share. However, only `const` values can be passed across a service
         * boundary.
         *
         * (Note: The term "persistent" is not related to the concept of persistent storage.)
         */
        Persistent,
        /*
         * A _Fixed-Size_ array structure allows mutations via replacement of its elements, but
         * not any mutations that would add or remove elements and thus altering its `size`.
         */
        Fixed,
        /*
         * A _Mutable_  array structure allows mutations, including changes in its `size`. Elements
         * may be added, removed, and replaced in-place.
         */
        Mutable
    }

    /**
     * The Mutability of the array structure.
     */
    @Override
    public/private Mutability mutability;


    // ----- Duplicable interface ------------------------------------------------------------------

    @Override
    Array duplicate() {
        return this.inPlace && !this.is(immutable)
                ? this.new(this)
                : this;
    }


    // ----- Freezable interface -------------------------------------------------------------------

    /**
     * Return a `const` array of the same type and contents as this array.
     *
     * All mutating calls to a `const` array will result in the creation of a new
     * `const` array with the requested changes incorporated.
     *
     * @param inPlace  pass True to indicate that the Array should make a frozen copy of itself if
     *                 it does not have to; the reason that making a copy is the default behavior is
     *                 to protect any object that already has a reference to the unfrozen array
     *
     * @throws Exception if any of the values in the array are not `service`, not `const`, and not
     *         [Freezable]
     */
    @Override
    immutable Array freeze(Boolean inPlace = False) {
        if (&this.isImmutable) {
            return this.as(immutable Array);
        }

        if (delegate.mutability == Constant) {
            // the underlying delegate is already frozen
            assert &delegate.isImmutable;
            mutability = Constant;
            return this.makeImmutable();
        }

        if (!inPlace) {
            return new Array(Constant, this).as(immutable Array);
        }

        // all elements must be immutable or Freezable (or exempt, i.e. a service); do not short
        // circuit this check, since we want to fail *before* we start freezing anything if the
        // array contains *any* non-freezable elements
        Boolean convert = False;
        for (Element element : this) {
            convert |= requiresFreeze(element);
        }

        if (convert) {
            loop: for (Element element : this) {
                if (Element+Freezable notYetFrozen := requiresFreeze(element)) {
                    this[loop.count] = notYetFrozen.freeze();
                }
            }
        }

        mutability = Constant;
        return makeImmutable();
    }


    // ----- Array interface -----------------------------------------------------------------------

    /**
     * The capacity of an array is the amount that the array can hold without resizing.
     */
    @Override
    Int capacity {
        @Override
        Int get() {
            return delegate.capacity;
        }

        @Override
        void set(Int newCap) {
            Int oldCap = get();
            if (newCap == oldCap) {
                return;
            }

            assert newCap >= 0 && newCap >= size && mutability == Mutable;
            delegate.capacity = newCap;
        }
    }

    /**
     * Fill the specified elements of this array with the specified value.
     *
     * @param value     the value to use to fill the array
     * @param interval  an optional interval of element indexes, defaulting to the entire array
     */
    Array fill(Element value, Interval<Int>? interval = Null) {
        Interval<Int> entire = 0 ..< size;
        if (interval == Null) {
            if (empty) {
                return this;
            }
            interval = entire;
        } else {
            assert interval.effectiveLowerBound >= 0 &&
                    (entire.covers(interval) || mutability != Fixed && interval.adjoins(entire));
        }

        if (inPlace) {
            for (Int i : interval) {
                this[i] = value;
            }
            return this;
        } else {
            Int   size   = this.size.notLessThan(interval.effectiveUpperBound + 1);
            Array result = new Element[size](i -> (interval.contains(i) ? value : this[i]));
            return mutability == Constant
                    ? result.freeze(True)
                    : result.toArray(mutability, True);
        }
    }


    // ----- UniformIndexed interface --------------------------------------------------------------

    @Override
    @Op("[]")
    Element getElement(Int index) {
        return delegate.elementAt(index).get();
    }

    @Override
    @Op("[]=")
    void setElement(Int index, Element value) {
        if (!inPlace) {
            throw new ReadOnly();
        }

        delegate.elementAt(index).set(value);
    }

    @Override
    Var<Element> elementAt(Int index) {
        return delegate.elementAt(index);
    }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    Boolean inPlace.get() {
        return switch (mutability) {
            case Mutable:
            case Fixed:      True;

            case Persistent:
            case Constant:   False;
        };
    }

    @Override
    Int size.get() {
        return delegate.size;
    }

    @Override
    Boolean contains(Element value) {
        // use the default implementation from the List interface
        return indexOf(value);
    }

    @Override
    @Op("+")
    Array add(Element element) {
        switch (mutability) {
        case Mutable:
            delegate.insert(size, element);
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            Element[] result = new Element[size + 1](i -> (i < size ? this[i] : element));
            return mutability == Persistent
                    ? result.toArray(Persistent, True)
                    : result.freeze(True);
        }
    }

    @Override
    @Op("+")
    Array addAll(Iterable<Element> values) {
        switch (mutability) {
        case Mutable:
            for (Element value : values) {
                add(value);
            }
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            Iterator<Element> iter = values.iterator();
            function Element (Int) supply = i -> {
                if (i < size) {
                    return this[i];
                }
                assert Element value := iter.next();
                return value;
            };

            Element[] result = new Element[this.size + values.size](supply);
            return mutability == Persistent
                    ? result.toArray(Persistent, True)
                    : result.freeze(True);
        }
    }

    @Override
    (Array, Int) removeAll(function Boolean (Element) shouldRemove) {
        Int[]? indexes = Null;
        loop: for (Element value : this) {
            if (shouldRemove(value)) {
                indexes = (indexes ?: new Int[]) + loop.count;
            }
        }

        if (indexes == Null) {
            return this, 0;
        }

        if (mutability == Fixed) {
            throw new ReadOnly("element removal unsupported");
        }

        if (indexes.size == 1) {
            return delete(indexes[0]), 1;
        }

        // copy everything except the "shouldRemove" elements to a new array
        Int       newSize      = size - indexes.size;
        Element[] result       = new Element[](newSize);
        Int       delete       = indexes[0];
        Int       deletedCount = 0;
        for (Int index = 0; index < size; ++index) {
            if (index == delete) {
                // obtain the next element index to delete
                delete = ++deletedCount < indexes.size ? indexes[deletedCount] : MaxValue;
            } else {
                result += this[index];
            }
        }

        switch (mutability) {
        case Mutable:
            deleteAll(newSize ..< size);
            for (Int i : 0 ..< newSize) {
                this[i] = result[i];
            }
            return this, deletedCount;

        case Persistent:
            return result.toArray(Persistent, True), deletedCount;

        case Constant:
            return result.freeze(True), deletedCount;

        default: assert;
        }
    }

    @Override
    Array clear() {
        if (empty) {
            return this;
        }

        switch (mutability) {
        case Mutable:
            delegate = new Element[];
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            return new Array<Element>(mutability, []);
        }
    }

    @Override
    Array! toArray(Mutability? mutability = Null, Boolean inPlace = False) {
        if (mutability == Null || mutability == this.mutability) {
            return this;
        }

        if (mutability == Constant) {
            return freeze(inPlace);
        }

        if (!inPlace || mutability > this.mutability) {
            return new Array(mutability, this);  // return a copy that has the desired mutability
        }

        this.mutability = mutability;
        return this;
    }

    @Override
    Array! reify(Mutability? mutability = Null) {
        ArrayDelegate<Element> reifiedDelegate = delegate.reify(mutability);
        return &delegate == &reifiedDelegate
                ? this
                : new Array<Element>(reifiedDelegate, mutability ?: this.mutability);
    }


    // ----- List interface ------------------------------------------------------------------------

    @Override
    Array filterIndexed(function Boolean(Element, Int) match,
                        Array?                         dest = Null) {
       return super(match, dest).as(Array);
    }

    @Override
    Array reversed(Boolean inPlace = False) {
        Array result = super(inPlace).as(Array);
        return result.mutability == this.mutability
                ? result
                : result.toArray(this.mutability, inPlace = True);
    }

    @Override
    Array shuffled(Boolean inPlace = False) {
        return super(inPlace).as(Array);
    }

    @Override
    Array replace(Int index, Element value) {
        if (inPlace) {
            this[index] = value;
            return this;
        } else {
            Element[] result = new Element[size](i -> (i == index ? value : this[i]));
            return mutability == Persistent
                    ? result.toArray(Persistent, True)
                    : result.freeze(True);
        }
    }

    @Override
    Array insert(Int index, Element value) {
        if (index == size) {
            return this + value;
        }

        assert:bounds index >= 0 && index < size;

        switch (mutability) {
        case Mutable:
            val newDelegate = delegate.insert(index, value);
            assert &newDelegate == &delegate;
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            val newDelegate = delegate.insert(index, value);
            assert &newDelegate != &delegate;
            return new Array<Element>(newDelegate, mutability);
        }
    }

    @Override
    Array insertAll(Int index, Iterable<Element> values) {
        Int addSize = values.size;
        if (addSize == 0) {
            return this;
        }

        if (addSize == 1) {
            return insert(index, values.iterator().take());
        }

        Int oldSize = size;
        if (index == oldSize) {
            return this + values;
        }

        assert:bounds index >= 0 && index < oldSize;

        Int newSize = oldSize + addSize;
        Element[] newDelegate = new Element[](newSize);
        for (Int from : 0 ..< index) {
            newDelegate.insert(from, this[from]);
        }

        Int to = index;
        for (Element v : (mutability == Constant ? values.toArray(Constant) : values)) {
            newDelegate.insert(to++, v);
        }

        for (Int from : index ..< oldSize) {
            newDelegate.insert(to++, this[from]);
        }

        switch (mutability) {
        case Mutable:
            delegate = newDelegate;
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            return new Array<Element>(newDelegate.toArray(mutability), mutability);
        }
    }

    @Override
    Array delete(Int index) {
        assert:bounds index >= 0 && index < size;

        switch (mutability) {
        case Mutable:
            val newDelegate = delegate.delete(index);
            assert &newDelegate == &delegate;
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            val newDelegate = delegate.delete(index);
            assert &newDelegate != &delegate;
            return new Array<Element>(newDelegate, mutability);
        }
    }

    @Override
    Array deleteAll(Interval<Int> indexes) {
        Int lo = indexes.effectiveLowerBound;
        Int hi = indexes.effectiveUpperBound;
        switch (lo <=> hi) {
        case Lesser:
            break;

        case Equal:
            return delete(lo);

        case Greater:
            // e.g. a range like [3..3)
            return this;
        }

        Int size = size;
        assert:bounds 0 <= lo < hi < size;

        Int removing = hi - lo + 1;
        if (removing == size) {
            return clear();
        }

        switch (mutability) {
        case Mutable:
            val newDelegate = new Element[];
            if (lo > 0) {
                newDelegate += this[0 ..< lo];
            }
            if (hi+1 < size) {
                newDelegate += this[hi+1 ..< size];
            }
            delegate = newDelegate;
            return this;

        case Fixed:
            throw new ReadOnly("Fixed size array");

        case Persistent:
        case Constant:
            Element[] result = new Element[size](i -> this[i < lo ? i : i+removing]);
            return mutability == Persistent
                    ? result.toArray(Persistent, True)
                    : result.freeze(True);
        }
    }

    @Override
    @Op("[..]") Array slice(Range<Int> indexes) {
        ArrayDelegate<Element> slice = indexes.effectiveFirst == 0 && indexes.size == this.size
                ? this
                : new SlicingDelegate<Element>(delegate, indexes);

        return new Array(slice, Fixed);

        static class SlicingDelegate<Element>(ArrayDelegate<Element> unsliced, Range<Int> indexes)
                implements ArrayDelegate<Element> {
            @Override
            construct(SlicingDelegate that) {
                // we're not actually copying any data here, since this is just a view on an
                // underlying array
                construct SlicingDelegate(that.unsliced, that.indexes);
            }

            @Override
            SlicingDelegate duplicate() {
                return this;
            }

            @Override
            Mutability mutability.get() {
                Mutability underlying = unsliced.mutability;
                return underlying == Mutable ? Fixed : underlying;
            }

            @Override
            Int capacity {
                @Override
                Int get() {
                    return size;
                }

                @Override
                void set(Int newCapacity) {
                    if (newCapacity != get()) {
                        throw new ReadOnly();
                    }
                }
            }

            @Override
            Int size.get() {
                return indexes.size;
            }

            @Override
            Var<Element> elementAt(Int index) {
                return unsliced.elementAt(translateIndex(index));
            }

            @Override
            Array<Element> insert(Int index, Element value) {
                throw new ReadOnly();
            }

            @Override
            Array<Element> delete(Int index) {
                throw new ReadOnly();
            }

            @Override
            Array<Element> reify(Mutability? mutability = Null) {
                Element[] reified = new Element[size](i -> elementAt(i).get());
                return new Array<Element>(reified, mutability ?: this.mutability);
            }

            /**
             * Translate from an index into a slice, into an index into the underlying array.
             *
             * @param index  the index into the slice
             *
             * @return the corresponding index into the underlying array
             */
            private Int translateIndex(Int index) {
                assert:bounds index >= 0 && index < indexes.size;
                return indexes.descending
                        ? indexes.effectiveUpperBound - index
                        : indexes.effectiveLowerBound + index;
            }
        }
    }


    // ----- List extensions -----------------------------------------------------------------------

    /**
     * Remove the specified element, but instead of deleting it from the middle of the array, move
     * the last element of the array into the position previously occupied by the element and then
     * delete the last element of the array.
     *
     * @param value  the value to remove from this array
     *
     * @return the resulting array, which is always `this`
     *
     * @throws ReadOnly  if the array does not support in-place element modification and deletion
     */
    Array removeUnordered(Element value) {
        if (Int index := indexOf(value)) {
            return deleteUnordered(index);
        }

        return this;
    }

    /**
     * Replace the element at the specified index with the element from the end of the array, and
     * then delete the last element of the array. This avoids shifting the contents of the entire
     * remainder of the array.
     *
     * @param index  the index of the element to delete, which must be between `0` (inclusive)
     *               and `size` (exclusive)
     *
     * @return the resulting array, which is always `this`
     *
     * @throws ReadOnly     if the array does not support in-place element modification and deletion
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (exclusive)
     */
    Array deleteUnordered(Int index) {
        if (!inPlace) {
            throw new ReadOnly();
        }

        Int     lastIndex   = size - 1;
        Element lastElement = this[lastIndex];
        delete(lastIndex);
        if (index < lastIndex) {
            this[index] = lastElement;
        }
        return this;
    }


    // ----- Comparable ----------------------------------------------------------------------------

    /**
     * Compare two arrays of the same type for equality.
     *
     * @return True iff the arrays have the same size, and for each index _i_, the element at that
     *         index from each array is equal
     */
    static <CompileType extends Array> Boolean equals(CompileType value1, CompileType value2) {
        Int size = value1.size;
        if (value2.size != size) {
            return False;
        }

        for (Int i : 0 ..< size) {
            if (value1[i] != value2[i]) {
                return False;
            }
        }

        return True;
    }


    // ----- Orderable mixin -----------------------------------------------------------------------

    private static mixin OrderableArray<Element extends Orderable>
            into Array<Element>
            implements Orderable {
        // ----- aggregation -------------------------------------------------------------------

        /**
         * Compute the minimal value in this array.
         *
         * @return True iff the array is not empty
         * @return (conditional) the minimum element value
         */
        conditional Element min() {
            switch (Int size = size) {
            case 0:
                return False;
            case 1:
                return True, this[0];
            default:
                Element min = this[0];
                for (Int i = 1; i < size; ++i) {
                    val element = this[i];
                    if (element < min) {
                        min = element;
                    }
                }
                return True, min;
            }
        }

        /**
         * Compute the maximal value in this array.
         *
         * @return True iff the array is not empty
         * @return (conditional) the maximum element value
         */
        conditional Element max() {
            switch (Int size = size) {
            case 0:
                return False;
            case 1:
                return True, this[0];
            default:
                Element max = this[0];
                for (Int i = 1; i < size; ++i) {
                    val element = this[i];
                    if (element > max) {
                        max = element;
                    }
                }
                return True, max;
            }
        }

        /**
         * Compute the range of values in this array.
         *
         * @return True iff the array is not empty
         * @return (conditional) the minimum value
         * @return (conditional) the maximum value
         */
        conditional (Element min, Element max) range() {
            switch (Int size = size) {
            case 0:
                return False;
            case 1:
                val element = this[0];
                return True, element, element;
            default:
                Element min = this[0];
                Element max = min;
                for (Int i : 1 ..< size) {
                    Element next = this[i];
                    if (next < min) {
                        min = next;
                    } else if (next > max) {
                        max = next;
                    }
                }
                return True, min, max;
            }
        }

        /**
         * Compute the median of values in this array using the [Quickselect algorithm]
         * (https://en.wikipedia.org/wiki/Quickselect).
         *
         * @return one of: (i) an empty array if this array is empty; (ii) an array of one element
         *         iff the array has an odd number of elements or the two median elements of this
         *         evenly sized array are equal; or, (iii) the two median elements of this evenly
         *         sized array are not equal
         */
        Element[] median() {
            switch (Int size = size) {
            case 0:
                return [];
            case 1:
                return [this[0]];
            default:
                // this algorithm partially sorts the array, so we need a modifiable copy
                Element[] array = new Array<Element>(Fixed, this);
                (Element? a, Element? b) = array.quickSelect(Null, Null, 0, size - 1, size / 2);
                assert a != Null && b != Null;

                if (size & 1 == 0 && a != b) {
                    // even case
                    return [a, b];
                } else {
                    // odd case
                    return [b];
                }
            }
        }

        /**
         * The implementation of the
         * [Quickselect algorithm](https://en.wikipedia.org/wiki/Quickselect) computing the k-th
         * smallest element in an array.
         */
        protected (Element? a, Element? b) quickSelect(Element? a, Element? b, Int l, Int r, Int k) {
            if (l <= r) {
                Int partitionIndex = partition(l, r);

                if (partitionIndex == k) {
                    // we found the median of an odd number of elements
                    b = this[partitionIndex];
                    if (a != Null) {
                        return a, b;
                    }
                } else if (partitionIndex == k - 1) {
                    // we get a & b as middle element of the array
                    a = this[partitionIndex];
                    if (b != Null) {
                        return a, b;
                    }
                }

                if (partitionIndex >= k) {
                    // find the index in first half
                    return quickSelect(a, b, l, partitionIndex - 1, k);
                } else {
                    return quickSelect(a, b, partitionIndex + 1, r, k);
                }
            }
            return a, b;

            Int partition(Int l, Int r) {
                Element pivot = this[r];
                Int i = l;
                Int j = l;
                while (j < r) {
                    if (this[j] < pivot) {
                        swap(i, j);
                        i++;
                    }
                    j++;
                }
                swap(i, r);
                return i;
            }
        }

        // ----- Orderable interface -----------------------------------------------------------

        /**
         * Compare two arrays of the same type for purposes of ordering.
         */
        static <CompileType extends OrderableArray>
                Ordered compare(CompileType array1, CompileType array2) {
            for (Int i = 0, Int c = Int.minOf(array1.size, array2.size); i < c; i++) {
                Ordered order = array1[i] <=> array2[i];
                if (order != Equal) {
                    return order;
                }
            }

            return array1.size <=> array2.size;
        }
    }


    // ----- Hashable mixin ------------------------------------------------------------------------

    private static mixin HashableArray<Element extends Hashable>
            into Array<Element>
            implements Hashable {
        /**
         * Calculate a hash code for a given array.
         */
        static <CompileType extends HashableArray> Int64 hashCode(CompileType array) {
            Int64 hash = 0;
            for (CompileType.Element el : array) {
                hash += CompileType.Element.hashCode(el);
            }
            return hash;
        }
    }
}