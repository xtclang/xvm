/**
 * The `List` interface represents a collection of values stored in a specific order. An example of
 * a list is the linked list data structure, in which each item in the list points to the next item.
 *
 * While `List` supports indexed access, it does not guarantee that the access occurs in less than
 * `O(N)` time. An implementation of List that provides `O(1)` access time is said to be _indexed_;
 * all other implementations of List are said to _emulate_ indexing for those methods taking an
 * index as an argument. For all indexed access, the index is `0`-based.
 */
interface List<Element>
        extends Collection<Element>
        extends UniformIndexed<Int, Element>
        extends Sliceable<Int> {
    // ----- metadata ------------------------------------------------------------------------------

    @Override
    conditional Orderer? ordered() {
        // there is an order to a list, but (unless otherwise specified) not based on an Orderer
        return True, Null;
    }

    /**
     * Metadata: Does this `List` provide efficient O(1) access times when using the index-based
     * methods to access values from the List?
     */
    @RO Boolean indexed.get() {
        return True;
    }


    // ----- read operations -----------------------------------------------------------------------

    @Override
    Iterator<Element> iterator() {
        // implementations that are not indexed should provide a more efficient implementation
        return new Iterator() {
            private Int i = 0;

            @Override
            conditional Element next() {
                if (i < this.List.size) {
                    return True, this.List[i++];
                }
                return False;
            }
        };
    }

    @Override
    Boolean contains(Element value) {
        if (Orderer? orderer := ordered(), orderer != Null) {
            // binary search
            Int lo = 0;
            Int hi = size - 1;
            while (lo <= hi) {
                Int     mid = (lo + hi) >> 1;
                Element cur = this[mid];
                switch (orderer(value, cur)) {
                case Lesser:
                    hi = mid - 1;
                    break;
                case Equal:
                    return True;
                case Greater:
                    lo = mid + 1;
                    break;
                }
            }
            return False;
        }

        if (indexed, Int size := knownSize()) {
            for (Int i = 0; i < size; ++i) {
                if (this[i] == value) {
                    return True;
                }
            }
            return False;
        }

        return super(value);
    }

    /**
     * Obtain the first element in the list.
     *
     * @return True iff the list is not empty
     * @return the first element in the list
     */
    conditional Element first() {
        if (empty) {
            return False;
        }
        return True, this[0];
    }

    /**
     * Obtain the last element in the list.
     *
     * @return True iff the list is not empty
     * @return the last element in the list
     */
    conditional Element last() {
        if (empty) {
            return False;
        }

        if (indexed, Int size := knownSize()) {
            return True, this[size-1];
        }

        Iterator<Element> iter = iterator();
        assert Element value := iter.next();
        while (value := iter.next()) {
        }
        return True, value;
    }

    /**
     * Determine if `this` list _starts-with_ `that` list. A list `this` of at least `n`
     * elements "starts-with" another list `that` of exactly `n` elements iff, for each index
     * `0..<n`, the element at the index in `this` list is equal to the element at the same
     * index in `that` list.
     *
     * @param that  a list to look for at the beginning of this list
     *
     * @return True iff this list starts-with that list
     */
    Boolean startsWith(List! that) {
        if (Int thisSize := this.knownSize(),
            Int thatSize := that.knownSize()) {
            if (thatSize == 0) {
                return True;
            }

            if (thisSize < thatSize) {
                return False;
            }

            if (this.indexed && that.indexed) {
                for (Int i : 0 ..< thatSize) {
                    if (this[i] != that[i]) {
                        return False;
                    }
                }
                return True;
            }
        }

        // fall-back: use iterators to compare the items in the two lists
        using (Iterator<Element> thisIter = this.iterator(),
               Iterator<Element> thatIter = that.iterator()) {
           while (Element thatElem := thatIter.next()) {
                if (Element thisElem := thisIter.next()) {
                    continue;
                } else {
                    return False;
                }
            }
        }
        return True;
    }

    /**
     * Determine if `this` list _ends-with_ `that` list. A list `this` of `m` elements
     * "ends-with" another list `that` of `n` elements iff `n <= m` and, for each index `i`
     * in the range `0..<n`, the element at the index `m-n+i` in `this` list is equal to the
     * element at index `i` in `that` list.
     *
     * @param that  a list to look for at the end of this list
     *
     * @return True iff this list end-with that list
     */
    Boolean endsWith(List! that) {
        Int thatSize = that.size;
        if (thatSize == 0) {
            return True;
        }

        Int thisSize = this.size;
        if (thisSize < thatSize) {
            return False;
        }

        Int offset = thisSize - thatSize;
        if (this.indexed && that.indexed) {
            for (Int i : 0 ..< thatSize) {
                if (this[offset+i] != that[i]) {
                    return False;
                }
            }
            return True;
        }

        Iterator<Element> thisIter = this.iterator();
        Iterator<Element> thatIter = that.iterator();
        for (Int i : 0 ..< offset) {
            assert thisIter.next();
        }
        while (Element thatVal := thatIter.next()) {
            assert Element thisVal := thisIter.next();
            if (thisVal != thatVal) {
                return False;
            }
        }
        assert !thisIter.next();
        return True;
    }

    /**
     * Look for the specified `value` starting at the specified index.
     *
     * @param value    the value to search for
     * @param startAt  the first index to search from (optional)
     *
     * @return True iff this list contains the `value`, at or after the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int indexOf(Element value, Int startAt = 0) {
        if (Orderer? orderer := ordered(), orderer != Null) {
            if (Int index := binarySearch(value, orderer)) {
                while (index > startAt && this[index-1] == value) {
                    --index;
                }
                return True, index;
            }
            return False;
        }

        if (indexed, Int size := knownSize()) {
            for (Int i = startAt.notLessThan(0); i < size; ++i) {
                if (this[i] == value) {
                    return True, i;
                }
            }
            return False;
        }

        Loop: for (Element e : iterator()) {
            if (Loop.count >= startAt && e == value) {
                return True, Loop.count;
            }
        }
        return False;
    }

    /**
     * Search the list using the supplied function.
     *
     * @param match    the match function to use
     * @param startAt  the first index to search from (optional)
     *
     * @return True iff a match was found
     * @return the index at which the specified value was found
     */
    conditional Int indexOf(function Boolean(Element) match, Int startAt = 0) {
        if (indexed, Int size := knownSize()) {
            for (Int i = startAt.notLessThan(0); i < size; ++i) {
                if (match(this[i])) {
                    return True, i;
                }
            }
            return False;
        }

        Loop: for (Element e : iterator()) {
            if (Loop.count >= startAt && match(e)) {
                return True, Loop.count;
            }
        }
        return False;
    }

    /**
     * Determine if `this` list _contains_ `that` list, and at what index `that` list
     * first occurs.
     *
     * @param that     a list to look for within this list
     * @param startAt  (optional) the first index to search from
     *
     * @return True iff this list contains that list, at or after the `startAt` index
     * @return (conditional) the index at which the specified list of values was found
     */
    conditional Int indexOf(List! that, Int startAt = 0) {
        Int length = that.size;
        startAt = startAt.notLessThan(0);
        if (length == 0) {
            return startAt > size ? False : (True, startAt);
        }

        Element firstMatch = that[0];
        Next: for (Int offset = startAt, Int stopAt = this.size - length; offset <= stopAt; ++offset) {
            if (this[offset] == firstMatch) {
                for (Int i = 1; i < length; ++i) {
                    if (this[offset + i] != that[i]) {
                        continue Next;
                    }
                }
                return True, offset;
            }
        }

        return False;
    }

    /**
     * Look for the specified `value` starting at the specified index and searching backwards.
     *
     * @param value    the value to search for
     * @param startAt  the index to start searching backwards from (optional)
     *
     * @return True iff this list contains the `value`, at or before the `startAt` index
     * @return (conditional) the index at which the specified value was found
     */
    conditional Int lastIndexOf(Element value, Int startAt = MaxValue) {
        if (Orderer? orderer := ordered(), orderer != Null) {
            if (Int index := binarySearch(value, orderer)) {
                Int stop = startAt.notGreaterThan(size-1);
                while (index < stop && this[index+1] == value) {
                    ++index;
                }
                return True, index;
            }

            return False;
        }

        if (indexed) {
            for (Int i = startAt.notGreaterThan(size-1); i >= 0; --i) {
                if (this[i] == value) {
                    return True, i;
                }
            }
            return False;
        }

        Int last = -1;
        Loop: for (Element e : iterator()) {
            if (Loop.count > startAt) {
                break;
            }
            if (e == value) {
                last = Loop.count;
            }
        }
        return last >= 0
                ? (True, last)
                : False;
    }

    /**
     * Search backwards the list using the supplied function.
     *
     * @param match    the match function to use
     * @param startAt  (optional) the index to start searching backwards from
     *
     * @return True iff a match was found
     * @return the index at which the specified value was found
     */
    conditional Int lastIndexOf(function Boolean(Element) match, Int startAt = 0) {
        if (indexed) {
            for (Int i = startAt.notGreaterThan(size-1); i >= 0; --i) {
                if (match(this[i])) {
                    return True, i;
                }
            }
            return False;
        }

        Int last = -1;
        Loop: for (Element e : iterator()) {
            if (Loop.count > startAt) {
                break;
            }
            if (match(e)) {
                last = Loop.count;
            }
        }
        return last >= 0
                ? (True, last)
                : False;
    }

    /**
     * Determine if `this` list _contains_ `that` list, and at what index `that` list
     * last occurs.
     *
     * @param that     a list to look for within this list
     * @param startAt  (optional) the index to start searching backwards from
     *
     * @return True iff this list contains that list, at or before the `startAt` index
     * @return (conditional) the index at which the specified list of values was found
     */
    conditional Int lastIndexOf(List! that, Int startAt = MaxValue) {
        Int length = that.size;
        startAt = startAt.notGreaterThan(this.size-length);
        if (length == 0) {
            return startAt < 0 ? False : (True, startAt);
        }

        Element firstMatch = that[0];
        Next: for (Int offset = startAt; offset >= 0; --offset) {
            if (this[offset] == firstMatch) {
                for (Int i = 1; i < length; ++i) {
                    if (this[offset + i] != that[i]) {
                        continue Next;
                    }
                }
                return True, offset;
            }
        }

        return False;
    }

    /**
     * Look for the specified `value` in the list, with the assumption that the list is ordered.
     *
     * @param value    the value to search for
     * @param compare  the ordering comparison function to use
     *
     * @return True iff this list contains the `value`
     * @return the index at which the specified value was found, or the insertion point if the
     *         value was not found
     */
    (Boolean found, Int index) binarySearch(Element value, Orderer? compare=Null) {
        if (compare == Null) {
            assert compare := Element.ordered();
        }

        return binarySearch(compare(value, _));
    }

    /**
     * Binary search the list using the supplied function.
     *
     * @param compare  the ordering comparison function to use
     *
     * @return True iff a match was found
     * @return the index at which the specified value was found, or the insertion point if the
     *         value was not found
     */
    (Boolean found, Int index) binarySearch(function Ordered(Element) order) {
        switch (Int size = this.size) {
        case 0:
            return False, 0;

        case 1:
            return switch (order(this[0])) {
            case Lesser:  (False, 0);
            case Equal:   (True,  0);
            case Greater: (False, 1);
        };

        case 2..4:
            // linear probe assumed to be faster than binary search for a small list
            Each: for (Element each : this) {
                switch (order(each)) {
                case Lesser:
                    return (False, Each.count);
                case Equal:
                    return (True,  Each.count);
                }
            }
            return False, size;

        default:
            Int first = 0;
            Int last  = size - 1;
            do {
                Int midpoint = (first + last) >> 1;
                switch (order(this[midpoint])) {
                case Lesser:
                    last = midpoint - 1;
                    break;
                case Equal:
                    return True, midpoint;
                case Greater:
                    first = midpoint + 1;
                    break;
                }
            } while (first <= last);
            return False, first;
        }
    }

    /**
     * Evaluate the contents of this `Collection` using the provided criteria, and produce a
     * resulting `Collection` that contains only the elements that match.
     *
     * @param match  a function that evaluates an element of the `Collection` for inclusion
     * @param dest   an optional `Collection` to collect the results in; pass `this` collection to
     *               filter out the values "in place"
     *
     * @return the resulting `Collection` containing the elements that matched the criteria
     */
    List! filterIndexed(function Boolean(Element, Int) match,
                        List?                          dest = Null) {
        if (&dest == &this) {
            Cursor cur = cursor();
            Int index = 0;
            while (cur.exists) {
                // note that since this is occurring "in place", the cursor index will not advance
                // after a node is deleted, so the original index is simulated with the counter
                if (match(cur.value, index++)) {
                    cur.advance();
                } else {
                    cur.delete();
                }
            }
            return this;
        }

        dest ?:= new Element[];
        Loop: for (Element e : this) {
            if (match(e, Loop.count)) {
                dest += e;
            }
        }
        return dest;
    }

    /**
     * Build a `Collection` that has one value "mapped from" each value in this `Collection`, using
     * the provided function.
     *
     * @param transform  a function that creates the "mapped" element from an element in this
     *                   `Collection`
     * @param dest       an optional `Collection` to collect the results in; pass `this` collection
     *                   to map the values "in place"
     *
     * @return the resulting `Collection` containing the elements that matched the criteria
     */
    <Result> List!<Result> mapIndexed(function Result(Element, Int) transform,
                                      List!<Result>?                dest = Null) {
        // in place
        if (&dest == &this) {
            assert inPlace;
            Cursor cur = cursor(0);
            while (cur.exists) {
                cur.value = transform(cur.value, cur.index).as(Element);
                cur.advance();
            }
            assert dest != Null;
            return dest;
        }

        dest ?:= new Result[];
        Loop: for (Element e : this) {
            dest.add(transform(e, Loop.count));
        }
        return dest;
    }

    /**
     * Reduce this Collection of elements to a result value using the provided function. This
     * operation is also called a _folding_ operation.
     *
     * @param initial     the initial value to start accumulating from
     * @param accumulate  the function that will be used to accumulate elements into a result
     *
     * @return the result of the reduction
     */
    <Result> Result reduceIndexed(Result                                initial,
                                  function Result(Result, Element, Int) accumulate) {
        Result result = initial;
        Loop: for (Element e : this) {
            result = accumulate(result, e, Loop.count);
        }
        return result;
    }

    /**
     * Chunk up this List into certain sized chunks, and evaluate each chunk, collecting the
     * results.
     *
     * @param size     the size of the window
     * @param process  the function to apply to each chunk
     *
     * @return the list of results from the `process` function
     */
    <Result> List!<Result> chunked(Int                            size,
                                   function Result(List<Element>) process) {
        assert:arg size > 0;
        Result[] results = new Result[];
        for (Int i = 0, Int thisSize = this.size; i < thisSize; i += size) {
            results.add(process(this[i ..< (i+size).notGreaterThan(thisSize)]));
        }
        return results;
    }

    /**
     * Slide a window over this List, evaluating the contents of the window as it moves using the
     * provided `process` function.
     *
     * @param size     the size of the window
     * @param process  the function to apply to each window
     * @param step     the amount to move the window on each iteration
     * @param partial  False indicates that only full-sized windows will be evaluated; True
     *                 allows partial windows to be evaluated
     *
     * @return the list of results from the `process` function on each window
     */
    <Result> List!<Result> windowed(Int                            size,
                                    function Result(List<Element>) process,
                                    Int                            step    = 1,
                                    Boolean                        partial = False) {
        assert:arg size > 0;
        Result[] results  = new Result[];
        Int      thisSize = this.size;
        Int      stop     = thisSize - (partial ? 1 : size);
        for (Int i = 0; i <= stop; i += step) {
            results.add(process(this[i ..< (i+size).notGreaterThan(thisSize)]));
        }
        return results;
    }

    /**
     * Sort the contents of this list in the order specified by the optional [Type.Orderer].
     *
     * @param order    (optional) the Orderer to use to sort the list, defaulting to using the
     *                 "natural" sort order of the Element type
     * @param inPlace  (optional) pass `True` to allow the List to sort itself "in place", if the
     *                 List is able to do so
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     */
    @Override
    List! sorted(Orderer? orderer = Null, Boolean inPlace = False) {
        Int size = this.size;
        if (size <= 1 && (inPlace || !this.inPlace)) {
            // nothing to sort
            return this;
        }

        if (orderer != Null, Orderer? prev := ordered(), prev? == orderer) {
            // already in the right order
            return inPlace
                    ? this
                    : this[0 ..< size];
        }

        return this.inPlace && inPlace
                ? sort(orderer)
                : super(orderer);
    }

    /**
     * Obtain a new List that represents the reverse order of this List.
     *
     * If a stable snapshot is required, then the caller must [reify](Collection.reify()] the
     * returned `List`.
     *
     * @param inPlace  pass `True` to allow the List to reverse its order in-place, without creating
     *                 a new List; note that the List implementation may still choose to create a
     *                 new List to satisfy the request, so this parameter is only a suggestion
     *
     * @return a List that is in the reverse order as this List
     */
    List! reversed(Boolean inPlace = False) {
        if (indexed, Int size := knownSize()) {
            if (size <= 1 && (inPlace || !this.inPlace)) {
                return this;
            }

            if (inPlace && this.inPlace) {
                // swap the elements in-place to reverse the list
                for (Int i = 0, Int swap = size >> 1; i < swap; ++i) {
                    Element e1 = this[i];
                    Element e2 = this[size - i - 1];

                    this[size - i - 1] = e1;
                    this[i]            = e2;
                }
                return this;
            }

            return this[size >.. 0];
        }

        return new Array<Element>(Mutable, this).reversed(True);
    }

    /**
     * Obtain a new List that represents a randomized order of this List.
     *
     * @param inPlace  pass `True` to allow the List to shuffle its order in-place, without creating
     *                 a new List; note that the List implementation may still choose to create a
     *                 new to satisfy the request, so this parameter is only used as a suggestion
     *
     * @return a List that contains this List's contents, but in a randomly-shuffled order
     */
    List! shuffled(Boolean inPlace = False) {
        if (Int size := knownSize()) {
            if (size <= 1 && (inPlace || !this.inPlace)) {
                return this;
            }

            if (inPlace && this.inPlace && indexed) {
                @Inject Random random;
                Loop: for (Element e : this) {
                    Int swapFrom = random.int(size);
                    if (Loop.count != swapFrom) {
                        Element swapValue = this[swapFrom];
                        this[swapFrom  ] = e;
                        this[Loop.count] = swapValue;
                    }
                }
                return this;
            }
        }

        return new Array<Element>(Mutable, this).shuffled(True);
    }

    @Override
    List reify() {
        // this method must be overridden by any implementing Collection that may return a view of
        // itself as a Collection, such that mutations to one might be visible from the other
        return this;
    }


    // ----- write operations ----------------------------------------------------------------------

    @Override
    @Op("-") List remove(Element value) {
        if (Int index := indexOf(value)) {
            return delete(index);
        }

        return this;
    }

    @Override
    conditional List removeIfPresent(Element value) {
        if (Int index := indexOf(value)) {
            return True, delete(index);
        }

        return False;
    }

    @Override
    (List, Int) removeAll(function Boolean (Element) shouldRemove) {
        List<Element> result = this;
        Int           count  = 0;

        if (inPlace && !indexed) {
            Cursor cur = cursor(0);
            while (cur.exists) {
                if (shouldRemove(cur.value)) {
                    cur.delete();
                    ++count;
                } else {
                    cur.advance();
                }
            }
        } else {
            Int index = 0;
            Int size  = this.size;
            while (index < size) {
                if (shouldRemove(result[index])) {
                    result = result.delete(index);
                    --size;
                    ++count;
                } else {
                    ++index;
                }
            }
        }

        return result, count;
    }

    /**
     * Replace the existing value in the List at the specified index with the specified value.
     *
     * @param index  the index at which to store the specified value, which must be between `0`
     *               (inclusive) and `size` (exclusive)
     * @param value  the value to store
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List replace(Int index, Element value) {
        if (inPlace && !this.is(immutable)) {
            this[index] = value;
            return this;
        }

        throw new ReadOnly($"{this:class} is immutable or does not support replace()");
    }

    /**
     * Replace existing values in the List with the provided values, starting at the specified
     * index.
     *
     * @param index   the index at which to store the specified value, which must be between `0`
     *                (inclusive) and `size` (exclusive)
     * @param values  the values to store
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive), or if the specified index plus the size of the
     *                      provided Iterable is greater than the size of this array
     */
    List replaceAll(Int index, Iterable<Element> values) {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to replace multiple elements efficiently
        Int  i      = index;
        List result = this;
        for (Element value : values) {
            result = result.replace(i++, value);
        }
        return result;
    }

    /**
     * Insert the specified value into the List at the specified index, shifting the contents of the
     * entire remainder of the list as a result. If the index is beyond the end of the list, this
     * operation has the same effect as calling [add].
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index  the index at which to insert, which must be between `0` (inclusive) and
     *               `size` (inclusive)
     * @param value  the value to insert
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List insert(Int index, Element value) {
        throw new ReadOnly($"{this:class} does not support insert()");
    }

    /**
     * Insert the specified values into the List at the specified index, shifting the contents of
     * the entire remainder of the list as a result. If the index is beyond the end of the list,
     * this operation has the same effect as calling [addAll].
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient insertion.
     *
     * @param index   the index at which to insert, which must be between `0` (inclusive) and
     *                `size` (inclusive)
     * @param values  the values to insert
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    List insertAll(Int index, Iterable<Element> values) {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to insert multiple elements efficiently
        Int  i      = index;
        List result = this;
        for (Element value : values) {
            result = result.insert(i++, value);
        }
        return result;
    }

    /**
     * Delete the element at the specified index, shifting the contents of the entire remainder of
     * the list as a result.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient deletion.
     *
     * @param index  the index of the element to delete, which must be between `0` (inclusive)
     *               and `size` (exclusive)
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (exclusive)
     */
    List delete(Int index) {
        throw new ReadOnly($"{this:class} does not support delete()");
    }

    /**
     * Delete the elements within the specified range, shifting the contents of the entire remainder
     * of the list as a result.
     *
     * Warning: This can be an incredibly expensive operation if the data structure is not
     * explicitly intended to support efficient deletion.
     *
     * @param indexes  the interval of indexes of the elements to delete, which must be between `0`
     *                 (inclusive) and `size` (exclusive)
     *
     * @return the resultant list, which is the same as `this` for a mutable list
     *
     * @throws OutOfBounds  if any of the specified indexes is outside of range `0` (inclusive) to
     *                      `size` (exclusive)
     */
    List deleteAll(Interval<Int> indexes) {
        // this implementation should be overridden by any non-mutable implementation of List, and
        // by any implementation that is able to delete multiple elements efficiently
        List result = this;
        for (Int index = indexes.effectiveLowerBound, Int count = indexes.size; count-- > 0; ) {
            result = result.delete(index);
        }
        return result;
    }


    // ----- List Cursor ---------------------------------------------------------------------------

    /**
     * Obtain a list cursor that is located at the specified index.
     *
     * @param index  the index within the list to position the cursor; (optional, defaults to the
     *               beginning of the list)
     *
     * @return a new Cursor positioned at the specified index in the List
     *
     * @throws OutOfBounds  if the specified index is outside of range `0` (inclusive) to
     *                      `size` (inclusive)
     */
    Cursor cursor(Int index = 0) {
        return new IndexCursor(index);
    }

    /**
     * A Cursor is a stateful, mutable navigator of a List. It can be used to walk through a list in
     * either direction, to randomly-access the list by altering its [index], can replace the
     * the values of elements in the list, insert or append new elements into the list, or delete
     * elements from the list.
     */
    interface Cursor
            extends Iterator<Element> {
        // ----- metadata ----------------------------------------------------------------------

        /**
         * The containing list.
         */
        @RO List list.get() {
            return this.List;
        }

        /**
         * Metadata: `True` iff the Cursor can move in reverse in an efficient manner.
         */
        @RO Boolean bidirectional.get() {
            return True;
        }


        // ----- Iterator methods --------------------------------------------------------------

        /**
         * Return the current element [value] and advance the `Cursor` to the next element.
         *
         * @return True iff `exists`
         * @return (conditional) the `Element` [value] that the `Cursor` was situated on
         */
        @Override
        conditional Element next() {
            if (exists) {
                Element value = this.value;
                advance();
                return True, value;
            }

            return False;
        }


        // ----- Cursor operations -------------------------------------------------------------

        /**
         * The current index of the cursor within the list, which is a value between `0`
         * (inclusive) and `size` (inclusive). If the index is equal to `size`, then the
         * cursor is "beyond the end of the list", and refers to a non-existent element.
         *
         * @throws OutOfBounds  if an attempt is made to set the index to a position less than
         *                      `0` or more than `size`
         */
        Int index;

        /**
         * True iff the index is less than the list size.
         */
        @RO Boolean exists;

        /**
         * This is the value of the element at the current index in the list. If the index is beyond
         * the end of the list, then the value cannot be accessed (an attempt will raise an
         * exception), but _setting_ the value is legal, and will append the specified value to the
         * end of the list.
         *
         * @throws ReadOnly     if the List is not _mutable_ or _fixed-size_
         * @throws OutOfBounds  if an attempt is made to access the value when the cursor is
         *                      beyond the end of the list
         */
        Element value;

        /**
         * Move the cursor so that it points to the _next_ element in the list. If there are no more
         * elements in the list, then the index will be set to `size`, and the cursor will be
         * "beyond the end of the list", and referring to a non-existent element.
         *
         * @return True if the cursor has advanced to another element in the list, or False if the
         *         cursor is now beyond the end of the list
         */
        Boolean advance() {
            Int index = this.index;
            Int size  = list.size;
            if (index < size) {
                this.index = ++index;
                return index < size;
            }
            return False;
        }

        /**
         * Move the cursor so that it points to the _previous_ element in the list. If there are no
         * elements preceding the current element in the list, then the index will be set to
         * `0`, and referring to the first element in the list.
         *
         * @return True if the cursor has rewound to another element in the list, or False if the
         *         cursor was already at the beginning of the list
         */
        Boolean rewind() {
            Int prev = index;
            if (prev > 0) {
                index = prev - 1;
                return True;
            } else {
                return False;
            }
        }

        /**
         * Insert the specified element at the current index, shifting the contents of the entire
         * remainder of the list "to the right" as a result. If the index is beyond the end of the
         * list, this operation has the same effect as setting the [Cursor.value] property.
         *
         * After this method completes successfully, the cursor will be positioned on the newly
         * inserted element.
         *
         * @throws ReadOnly  if the List is not _mutable_
         */
        void insert(Element value);

        /**
         * Delete the element at the current index, shifting the contents of the entire remainder of
         * the list "to the left" as a result.
         *
         * After this method completes successfully, the cursor will be positioned on the element
         * that immediately followed the element that was just deleted, or on the non-existent
         * element "beyond the end of the list" if the element deleted was the last element in the
         * list.
         *
         * @throws ReadOnly     if the List is not _mutable_
         * @throws OutOfBounds  if an attempt is made to delete the value when the cursor is
         *                      beyond the end of the list
         */
        void delete();
    }

    /**
     * An IndexCursor is a simple [List.Cursor] implementation that delegates all operations back to
     * a List using the index-based operations on the List itself.
     */
    class IndexCursor
            implements Cursor {
        /**
         * Construct an IndexCursor for the specified List.
         *
         * @param index  the location of the cursor in the list, between `0` (inclusive) and [List.size]
         *               (exclusive)
         */
        construct(Int index = 0) {
            if (index < 0 || index > size) {
                throw new OutOfBounds();
            }

            this.index = index;
        }

        @Override
        List<Element> list.get() {
            return this.List;
        }

        @Override
        Int index {
            @Override
            Int get() {
                return super().notGreaterThan(size);
            }

            @Override
            void set(Int i) {
                if (i < 0 || i > size) {
                    throw new OutOfBounds();
                }
                super(i);
            }
        }

        @Override
        Boolean exists.get() {
            return index < size;
        }

        @Override
        Boolean advance() {
            Int next = index + 1;
            index = next.notGreaterThan(size);
            return next < size;
        }

        @Override
        Boolean rewind() {
            Int prev = index;
            if (prev > 0) {
                index = prev - 1;
                return True;
            }
            return False;
        }

        @Override
        Element value {
            @Override
            Element get() {
                // may throw OutOfBounds
                return list[index];
            }

            @Override
            void set(Element value) {
                Int index = this.index;
                Int size  = this.List.size;
                if (index < size) {
                    // may throw ReadOnly
                    list[index] = value;
                } else if (this.is(immutable)) {
                    throw new ReadOnly($"{this:class} is immutable");
                } else if (inPlace) {
                    this.index = size;
                    add(value);
                } else {
                    throw new ReadOnly($"{this:class} does not support element set()");
                }
            }
        }

        @Override
        void insert(Element value) {
            if (!inPlace || this.is(immutable)) {
                throw new ReadOnly($"{this:class} is immutable or does not support insert()");
            }

            Int index = this.index;
            Int size  = this.List.size;
            if (index < size) {
                list.insert(index, value);
            } else {
                this.index = size;
                add(value);
            }
        }

        @Override
        void delete() {
            if (!inPlace || this.is(immutable)) {
                throw new ReadOnly($"{this:class} is immutable or does not support delete()");
            }

            Int index = this.index;
            Int size  = this.List.size;
            assert:bounds index < size;
            list.delete(index);
        }
    }


    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") List!<Element> slice(Range<Int> indexes) {
        return new SubList(indexes);
    }

    /**
     * An SubList is a simple [List] implementation that delegates all operations back to an
     * underlying List.
     */
    class SubList
            implements List<Element> {

        construct(Range<Int> indexes) {
            assert indexes.effectiveLowerBound >= 0;
            assert indexes.effectiveUpperBound < size;
            this.indexes = indexes;
        }

        protected/private Range<Int> indexes;

        @Override
        Int size.get() {
            return indexes.size;
        }

        @Override
        @Op("[]") Element getElement(Int index) {
            return this.List[indexes[index]];
        }

        @Override
        @Op("[]=") void setElement(Int index, Element value) {
            this.List[indexes[index]] = value;
        }

        @Override
        Iterator<Element> iterator() {
            Int first = indexes.effectiveFirst;
            Int last  = indexes.effectiveLast;

            return indexes.descending
                    ? new Iterator() {
                        private Int i = first;

                        @Override
                        conditional Element next() {
                            if (i >= last) {
                                return True, this.List[i--];
                            }
                            return False;
                        }
                    }
                    : new Iterator() {
                        private Int i = first;

                        @Override
                        conditional Element next() {
                            if (i <= last) {
                                return True, this.List[i++];
                            }
                            return False;
                        }
                    };
        }

        @Override
        @Op("[..]") List!<Element> slice(Range<Int> indexes) {
            return this.List.slice(this.indexes[indexes.effectiveFirst] .. this.indexes[indexes.effectiveLast]);
        }

        @Override
        List!<Element> reify() {
            return toArray();
        }
    }


    // ----- Equality ------------------------------------------------------------------------------

    /**
     * Two lists are equal iff they are of the same size, and they contain the same values, in the
     * same order.
     */
    static <CompileType extends List> Boolean equals(CompileType list1, CompileType list2) {
        if (Int size1 := list1.knownSize(),
            Int size2 := list2.knownSize()) {
            if (size1 != size2) {
                return False;
            }

            if (list1.indexed && list2.indexed) {
                for (Int i = 0; i < size1; ++i) {
                    if (list1[i] != list2[i]) {
                        return False;
                    }
                }
                return True;
            }
        }

        using (val iter1 = list1.iterator(),
               val iter2 = list2.iterator()) {
            while (val value1 := iter1.next()) {
                if (val value2 := iter2.next(), value1 == value2) {} else {
                    return False;
                }
            }
            return !iter2.next();
        }
    }


    // ----- sorting algorithms --------------------------------------------------------------------

    /**
     * Sort the contents of this list, in place if possible, using the specified order.
     *
     * @param order  (optional) the Orderer to use to sort the list; defaults to the natural order
     *               for Element
     *
     * @return the sorted list (which may not be the list that was passed in)
     */
    List!<Element> sort(Orderer? order = Null) {
        if (order == Null) {
            if (!(order := Element.ordered())) {
                throw new TypeMismatch($"Element type {Element} is not Orderable");
            }
        }

        if (this.is(immutable) || !inPlace) {
            return toArray(Mutable).sort(order);
        }

        Int size = this.size;
        if (size < 5) {
            bubbleSort(order);
        } else {
            quickSort(order, 0, size - 1);
        }

        return this;
    }

    /**
     * Bubble-sort the contents of this list, in place, using the specified Orderer. The loop
     * is optimized for an almost-sorted list, with the most-likely-to-be-unsorted items at the end.
     *
     * @param order  the Orderer to use to sort the list
     * @param max    the maximum number of passes to make over the list
     *
     * @return True iff the list is now sorted
     */
    Boolean bubbleSort(Orderer order, Int max = MaxValue) {
        if (!indexed) {
            // TODO cursor-based implementation?
        }

        Int last = size - 1;
        if (last <= 0) {
            return True;
        }

        Int     first = 0;
        Boolean sorted;
        do {
            sorted = True;

            Element bubble = this[last];
            for (Int i = last-1; i >= first; --i) {
                Element prev = this[i];
                if (order(prev, bubble) == Greater) {
                    this[i  ] = bubble;
                    this[i+1] = prev;
                    sorted    = False;
                } else {
                    bubble = prev;
                }
            }

            // the smallest item in the list has now bubbled up to the first position in the list;
            // optimize the next iteration by only bubbling up to the second position, and so on
            ++first;
        } while (!sorted && --max > 0);

        return sorted;
    }

    /**
     * Sort the contents of this list, in place, using the [Quicksort algorithm]
     * (https://en.wikipedia.org/wiki/Quicksort).
     *
     * @param order  the Orderer to use to sort the list
     * @paran low    the first index to sort
     * @paran high   the last index to sort
     */
    void quickSort(Orderer order, Int low, Int high) {

        if (low < high) {

            Int partitionIndex = partition(order, low, high);

            quickSort(order, low, partitionIndex - 1);
            quickSort(order, partitionIndex + 1, high);
        }

        /**
         * Partition this list by taking the last element as pivot, place the pivot element
         * at its correct position in sorted list, and place all smaller to left of pivot and all
         * greater elements to right of pivot.
         */
        Int partition(Orderer order, Int low, Int high) {
            Element pivot = this[high];
            Int     i     = low - 1;

            for (Int j : low ..< high) {

                if (order(this[j], pivot) == Lesser) {
                    i++;
                    swap(i, j);
                }
            }
            swap(i + 1, high);
            return i + 1;
        }
    }

    /**
     * Swap the elements of this list.
     */
    void swap(Int i, Int j) {
        Element el = this[i];
        this[i] = this[j];
        this[j] = el;
    }

    /**
     * Compute the longest consecutive run of the same value in the list. If the list is sorted,
     * the resulting value is the "mode" of values in this list, which is the value that appears
     * most often in this array.
     *
     * @return True iff the list is not empty
     * @return (optional) the longest run ("mode") of the list values
     * @return (optional) the size of the longest run
     */
    conditional (Element, Int) longestRun() {
        if (empty) {
            return False;
        }

        Element prevValue = this[0];
        Int     currCount = 1;
        Element maxValue  = prevValue;
        Int     maxCount  = currCount;

        for (Int i : 1 ..< size) {
            Element currValue = this[i];
            if (currValue == prevValue) {
                if (++currCount > maxCount) {
                    maxValue = currValue;
                    maxCount = currCount;
                }
            } else {
                prevValue = currValue;
                currCount = 1;
            }
        }

        return True, maxValue, maxCount;
    }
}