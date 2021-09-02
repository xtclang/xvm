/**
 * An iterator over a sequence of elements.
 */
interface Iterator<Element>
        extends Closeable
    {
    /**
     * An Orderer is a function that compares two objects for order.
     */
    typedef Element.Orderer Orderer;

    /**
     * Get the next element.
     *
     * It is expected that the Iterator will close itself when it exhausts its supply of elements.
     *
     * @return True iff an element is available
     * @return (conditional) an Element value
     */
    conditional Element next();

    /**
     * Take the next element.
     *
     * @return the Element value
     *
     * @throws IllegalState if the iterator is exhausted
     */
    Element take()
        {
        assert Element e := next();
        return e;
        }

    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param process  an action to perform on each element; if the action returns False, the
     *               iterator is considered "short-circuited", the method returns immediately
     *               and no more elements are iterated over
     *
     * @return True iff the iteration completed without short-circuiting; otherwise False if the
     *         iterator was short-circuited
     */
    Boolean whileEach(function Boolean process(Element))
        {
        while (Element value := next())
            {
            if (!process(value))
                {
                return False;
                }
            }
        return True;
        }

    /**
     * Perform the specified action for all remaining elements in the iterator, allowing for
     * a possibility to stop the iteration at any time.
     *
     * @param process  an action to perform on each element; if the action returns True, the
     *                 iterator is considered "short-circuited", the method returns immediately
     *                 and no more elements are iterated over
     *
     * @return True iff the iterator was short-circuited; otherwise False if the iteration
     *         completed without short-circuiting
     * @return (conditional) the element that caused the iterator to short-circuit
     */
    conditional Element untilAny(function Boolean process(Element))
        {
        while (Element value := next())
            {
            if (process(value))
                {
                return True, value;
                }
            }
        return False;
        }

    /**
     * Perform the specified action for all remaining elements in the iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param process  an action to perform on each element
     */
    void forEach(function void (Element) process)
        {
        while (Element value := next())
            {
            process(value);
            }
        }

    /**
     * Determine the minimum value contained in this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param order  (optional) the [Type.Orderer] to use to compare elements of this iterator; if
     *               none is provided, the Element type must be Orderable
     *
     * @return True iff the iterator is not empty and a minimum value was determined
     * @return (conditional) the minimum element from this iterator
     */
    conditional Element min(Orderer? order = Null)
        {
        if (order != Null, Orderer actual := knownOrder(), order == actual)
            {
            return next();
            }

        if (Element minValue := next())
            {
            if (order == Null)
                {
                assert Element.is(Type<Orderable>);

                while (Element el := next())
                    {
                    if (el < minValue)
                        {
                        minValue = el;
                        }
                    }
                }
            else
                {
                while (Element el := next())
                    {
                    if (order(el, minValue) == Lesser)
                        {
                        minValue = el;
                        }
                    }
                }
            return True, minValue;
            }
        return False;
        }

    /**
     * Determine the maximum value contained in this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param order  (optional) the [Type.Orderer] to use to compare elements of this iterator; if
     *               none is provided, the Element type must be Orderable
     *
     * @return True iff the iterator is not empty and a minimum value was determined
     * @return (conditional) the maximum element from this iterator
     */
    conditional Element max(Orderer? order = Null)
        {
        if (Element maxValue := next())
            {
            if (order == Null)
                {
                assert Element.is(Type<Orderable>);

                while (Element el := next())
                    {
                    if (el > maxValue)
                        {
                        maxValue = el;
                        }
                    }
                }
            else
                {
                while (Element el := next())
                    {
                    if (order(el, maxValue) == Greater)
                        {
                        maxValue = el;
                        }
                    }
                }
            return True, maxValue;
            }
        return False;
        }

    /**
     * Returns the interval defining the minimum and maximum elements of this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param order  (optional) the [Type.Orderer] to use to compare elements of this iterator; if
     *               none is provided, the Element type must be Orderable
     *
     * @return True iff the iterator is not empty and the range of values was determined
     * @return (conditional) the range of elements from this iterator
     */
    conditional Range<Element> range(Orderer? order = Null)
        {
        assert Element.is(Type<Orderable>);

        if (Element minValue := next())
            {
            Element maxValue = minValue;
            if (order == Null)
                {
                while (Element el := next())
                    {
                    switch (el <=> maxValue)
                        {
                        case Lesser:
                            minValue = el;
                            break;
                        case Greater:
                            maxValue = el;
                            break;
                        }
                    }
                }
            else
                {
                while (Element el := next())
                    {
                    switch (order(el, maxValue))
                        {
                        case Lesser:
                            minValue = el;
                            break;
                        case Greater:
                            maxValue = el;
                            break;
                        }
                    }
                }
            return True, minValue..maxValue;
            }
        return False;
        }

    /**
     * Determine the actual number of elements in this iterator, as if the iterator has to count
     * them.
     *
     * This iterator must not be used after this operation.
     *
     * @return the count of elements in this iterator
     */
    Int count()
        {
        if (knownEmpty())
            {
            return 0;
            }

        if (Int size := knownSize())
            {
            return size;
            }

        Int n = 0;
        while (Element el := next())
            {
            ++n;
            }
        return n;
        }

    /**
     * Obtain an array that contains all of the elements in this iterator. The contents will be
     * ordered if the iterator is ordered. There is no guarantee as to whether the returned array is
     * _mutable_, _fixed-size_, _persistent_, or `const`.
     *
     * This iterator must not be used after this operation.
     *
     * @return an array containing the elements of this iterator
     */
    Element[] toArray(Array.Mutability? mutability = Null)
        {
        Element[] elements;

        if (knownEmpty())
            {
            elements = [];
            }
        else if (Int size := knownSize())
            {
            elements = new Array<Element>(size, _ -> {assert Element el := next(); return el;});
            }
        else
            {
            elements = new Element[];
            while (Element el := next())
                {
                elements += el;
                }
            }

        return mutability == Null
                ? elements
                : elements.toArray(mutability, True);
        }


    // ----- metadata ------------------------------------------------------------------------------

    /**
     * Metadata: Are the elements of the iterator known to be distinct?
     */
    Boolean knownDistinct()
        {
        return False;
        }

    /**
     * Metadata: Is the iterator in an order that is a function of its elements? And if so, what is
     * the [Type.Orderer] that represents that ordering?
     *
     * @return True iff the iterator in an order that is a function of its elements
     * @return (conditional) the Orderer that represents the order
     */
    conditional Orderer knownOrder()
        {
        return False;
        }

    /**
     * Metadata: Is the iterator known to be empty?
     *
     * @return True iff the iterator is known to be empty; False if the iterator is not known to be
     *         empty (which means that it still _could_ be empty)
     */
    Boolean knownEmpty()
        {
        if (Int size := knownSize())
            {
            return size == 0;
            }

        return False;
        }

    /**
     * Metadata: Is the iterator of a known size?
     *
     * @return True iff the iterator size is efficiently known
     * @return (conditional) the number of elements in the iterator
     */
    conditional Int knownSize()
        {
        return False;
        }


    // ----- intermediate operations ---------------------------------------------------------------

    /**
     * Concatenate another iterator to this iterator, producing a new iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param that  the iterator to concatenate to this iterator
     *
     * @return a new iterator representing the concatenation of the two iterators
     */
    Iterator! concat(Iterator! that)
        {
        if (that.knownEmpty())
            {
            return this;
            }

        if (this.knownEmpty())
            {
            return that;
            }

        return new iterators.CompoundIterator(this, that);
        }

    /**
     * Returns a iterator consisting of the elements of this iterator that match the given predicate.
     *
     * This iterator must not be used after this operation.
     *
     * @param include  a predicate function to check if an element should be included
     *
     * @return a new iterator representing the filtered contents of this iterator
     */
    Iterator! filter(function Boolean (Element) include)
        {
        if (knownEmpty())
            {
            return this;
            }

        return new iterators.FilteredIterator<Element>(this, include);
        }

    /**
     * Returns a iterator consisting of the results of applying the given function to the elements of
     * this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param <Result>  the element type of the new iterator
     *
     * @param apply  a function to apply to each element of this iterator
     *
     * @return a new iterator representing the results of applying the specified function to each
     *         element in this iterator
     */
    <Result> Iterator!<Result> map(function Result (Element) apply)
        {
        return new iterators.MappedIterator<Result, Element>(this, apply);
        }

    /**
     * Returns a iterator consisting of the concatenation of all of the streams resulting from
     * applying the provided mapping function to each element of this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param <Result>  the element type of the new iterator
     * @param flatten   a function to apply to each element, resulting in a `Iterator<Result>`
     *
     * @return a new iterator representing the concatenated results of applying the specified function
     *         to each element in this iterator
     */
    <Result> Iterator!<Result> flatMap(function Iterator!<Result> (Element) flatten)
        {
        return new iterators.FlatMappedIterator<Result, Element>(this, flatten);
        }

    /**
     * Returns a iterator representing the _distinct_ elements of this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @return a new iterator representing only the distinct set of elements from this iterator
     */
    Iterator! dedup()
        {
        if (knownEmpty() || knownDistinct())
            {
            return this;
            }

        return new iterators.DistinctIterator<Element>(this);
        }

    /**
     * Returns a iterator representing the same elements of this iterator, but in a sorted order.
     *
     * This iterator must not be used after this operation.
     *
     * @param order  (optional) the [Type.Orderer] to use to sort the iterator's elements, or Null
     *               to indicate the natural sort order
     *
     * @return a new iterator representing the same elements from this iterator in a sorted order
     */
    Iterator! sorted(Orderer? order = Null)
        {
        if (order == Null)
            {
            assert Element.is(Type<Orderable>);
            order = (el1, el2) -> el1.as(Element) <=> el2.as(Element);
            }

        if (knownEmpty())
            {
            return this;
            }

        return toArray().sorted(order).iterator();
        }

    /**
     * Returns a iterator representing the same elements of this iterator, but in reverse order.
     *
     * **Warning:** This is likely to be an expensive operation.
     *
     * This iterator must not be used after this operation.
     *
     * @return a new iterator representing the same elements from this iterator, but in reverse order
     */
    Iterator! reversed()
        {
        if (knownEmpty())
            {
            return this;
            }

        return toArray().reversed().iterator();
        }

    /**
     * Returns a iterator representing the same elements as exist in this iterator, but additionally
     * performing the provided action on each element of the resulting iterator as elements are
     * consumed from it. This capability is considered particularly useful for debugging purposes.
     *
     * This iterator must not be used after this operation.
     *
     * @param accept  a function to perform on the elements as they are consumed from the resulting
     *                iterator
     *
     * @return a new iterator with the specified functionality attached to it
     */
    Iterator! peek(function void observe(Element))
        {
        if (knownEmpty())
            {
            return this;
            }

        return new iterators.PeekingIterator<Element>(this, observe);
        }

    /**
     * Returns a iterator representing only the remaining elements of this iterator after discarding
     * the first `count` elements of this iterator. If the iterator does not have enough elements
     * to skip the requested number of elements, then it simply skips as many as it holds, resulting
     * in an exhausted iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param count  the number of leading elements to skip in this iterator
     *
     * @return a new iterator that does not include the first `count` elements of this iterator
     */
    Iterator! skip(Int count)
        {
        assert:bounds count >= 0;

        if (count == 0 || knownEmpty())
            {
            return this;
            }

        if (Int size := knownSize(), size <= count)
            {
            return new iterators.ExhaustedIterator();
            }

        for (Int i = 0; i < count && next(); ++i)
            {
            }

        return this;
        }

    /**
     * Returns a iterator representing only the first `count` elements of this iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param count  the number of elements the resulting iterator should be limited to
     *
     * @return a new iterator that only includes up to the first `count` elements of this iterator
     */
    Iterator! limit(Int count)
        {
        assert:bounds count >= 0;

        if (knownEmpty())
            {
            return this;
            }

        if (count == 0)
            {
            return new iterators.ExhaustedIterator();
            }

        if (Int size := knownSize(), size <= count)
            {
            return this;
            }

        return new iterators.LimitedIterator<Element>(this, count);
        }

    /**
     * Returns a iterator representing only the specified range of elements of this iterator. If the
     * range extends beyond the scope of this iterator, then the missing elements are silently
     * omitted from the resulting iterator.
     *
     * This iterator must not be used after this operation.
     *
     * @param count  the number of leading elements to skip in this iterator
     *
     * @return a new iterator that does not include the first `count` elements of this iterator
     */
    Iterator! extract(Interval<Int> interval)
        {
        if (knownEmpty())
            {
            return this;
            }

        if (Int size := knownSize(), size < interval.effectiveLowerBound)
            {
            return new iterators.ExhaustedIterator();
            }

        if (interval.descending)
            {
            return reversed().extract(interval.reversed());
            }

        return interval.lowerBound == 0
                ? limit(interval.effectiveUpperBound)
                : skip(interval.effectiveLowerBound).limit(interval.size);
        }

    /**
     * Returns two independent copies of this iterator.
     *
     * **Warning:** This is likely to be an expensive operation.
     *
     * This iterator must not be used after this operation.
     *
     * @return a first clone of this iterator
     * @return a second clone of this iterator
     */
    (Iterator!, Iterator!) bifurcate()
        {
        if (knownEmpty())
            {
            return this, this;
            }

        Element[] snapshot = toArray();
        return snapshot.iterator(), snapshot.iterator();
        }


    // ----- advanced terminal operations ----------------------------------------------------------

    /**
     * Performs a reduction on the elements of this iterator, using the provided identity value and an
     * associative accumulation function, and returns the reduced value.
     *
     * The `identity` value must be an identity for the accumulator function. This means that
     * for all `el`, `accumulate(identity, el)` is equal to `el`.
     *
     * For example, to sum a iterator of Int values:
     *
     *   Iterator<Int> ints = ...
     *   Int sum = ints.reduce(0, (n1, n2) -> n1 + n2);
     *
     * This is a terminal operation.
     *
     * @param identity    the identity value for the accumulating function
     * @param accumulate  an associative, non-interfering, stateless function for
     *                    combining two values
     * @return the result of the reduction
     */
    Element reduce(Element identity, function Element accumulate(Element, Element))
        {
        Element result = identity;
        while (Element element := next())
            {
            result = accumulate(result, element);
            }
        return result;
        }

    /**
     * Performs a reduction on the elements of this iterator, using an associative accumulation
     * function, and returns a conditional reduced value, if any.
     *
     * For example, to concatenate a iterator of String values:
     *
     *   Iterator<String> strings = ...
     *   String concat = strings.reduce((s1, s2) -> s1 + s2);
     *
     * This is a terminal operation.
     *
     * @param accumulate  an associative function for combining two values
     *
     * @return a conditional result of the reduction
     */
    conditional Element reduce(function Element accumulate(Element, Element))
        {
        if (Element result := next())
            {
            while (Element element := next())
                {
                result = accumulate(result, element);
                }
            return True, result;
            }
        else
            {
            return False;
            }
        }


    // ----- Markable ------------------------------------------------------------------------------

    /**
     * Create a markable implementation of this iterator, if this iterator is not already
     * [Markable].
     *
     * @return a Markable Iterator
     */
    Iterator! + Markable ensureMarkable()
        {
        if (this.is(Markable))
            {
            return this;
            }

        return new MarkedIterator<Element>(this);
        }


    // ----- Closeable interface -------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null)
        {
        }
    }
