/**
 * A Set is a container data structure that represents a group of _distinct values_. While the Set's
 * interface is almost identical to that of the [Collection], its behavior is different due to its
 * requirement to maintain a distinct set of values.
 */
interface Set<Element>
        extends Collection<Element>
    {
    /**
     * The "union" operator.
     */
    @Override
    @Op("|") Set addAll(Iterable<Element> values);

    /**
     * The "relative complement" operator.
     */
    @Override
    @Op("-") Set removeAll(Iterable<Element> values);

    /**
     * The "intersection" operator.
     */
    @Override
    @Op("&") Set retainAll(Iterable<Element> values);

    /**
     * The "symmetric difference" operator determines the elements that are present in only this
     * set or the other set, but not both.
     *
     *   A ^ B = (A - B) | (B - A)
     *
     * A `Mutable` set will perform the operation in place; persistent sets will return a new set
     * that reflects the requested changes.
     *
     * @param values  another set containing values to determine the symmetric difference with
     *
     * @return the resultant set, which is the same as `this` for a mutable set
     */
    @Op("^") Set symmetricDifference(Set!<Element> values)
        {
        Set<Element> result = this;
        Clone: if (result.inPlace && result.is(immutable))
            {
            if (result.is(Replicable))
                {
                result = result.new();
                if (!(result.inPlace && result.is(immutable)))
                    {
                    result = result.addAll(this);
                    break Clone;
                    }
                }

            throw new ReadOnly($"{this:class} is immutable or does not support symmetricDifference()");
            }

        Element[]? remove = Null;
        for (Element value : this)
            {
            if (values.contains(value))
                {
                remove = (remove ?: new Element[]) + value;
                }
            }

        Element[]? add = Null;
        for (Element value : values)
            {
            if (!this.contains(value))
                {
                add = (add ?: new Element[]) + value;
                }
            }

        result -= remove?;
        result |= add?;
        return result;
        }

    /**
     * The "complement" operator.
     *
     * @param universalSet  the set from which this set is drawn, and within which the complement
     *                      will be calculated
     *
     * @return a new set that represents the complement of this set within the `universalSet`
     */
    @Concurrent
    @Op("~") Set! complement(immutable Set!<Element> universalSet)
        {
        return new ComplementSet<Element>(this, universalSet);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String                    sep    = ", ",
            String?                   pre    = "{",
            String?                   post   = "}",
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null)
        {
        return super(sep, pre, post, limit, trunc, render);
        }

    @Override
    Appender<Char> appendTo(
            Appender<Char>            buf,
            String                    sep    = ", ",
            String?                   pre    = "{",
            String?                   post   = "}",
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null)
        {
        return super(buf, sep, pre, post, limit, trunc, render);
        }
    }