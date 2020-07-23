/**
 * A Set is a container data structure that represents a group of _distinct values_. While the Set's
 * interface is identical to that of the Collection, its default behavior is subtly different.
 */
interface Set<Element>
        extends Collection<Element>
        extends Stringable
    {
    // ----- read operations -----------------------------------------------------------------------

    /**
     * A Set is always composed of distinct values.
     */
    @Override
    @RO Boolean distinct.get()
        {
        return true;
        }

    /**
     * The "union" operator.
     */
    @Override
    @Op("|")
    Set addAll(Iterable<Element> values);

    /**
     * The "relative complement" operator.
     */
    @Override
    @Op("-")
    Set removeAll(Iterable<Element> values);

    /**
     * The "intersection" operator.
     */
    @Override
    @Op("&")
    Set retainAll(Iterable<Element> values);

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
    @Op("^")
    Set symmetricDifference(Set!<Element> values)
        {
        Element[]? remove = null;
        for (Element value : this)
            {
            if (values.contains(value))
                {
                remove = (remove ?: new Element[]) + value;
                }
            }

        Element[]? add = null;
        for (Element value : values)
            {
            if (!this.contains(value))
                {
                add = (add ?: new Element[]) + value;
                }
            }

        Set<Element> result = this;
        result -= remove?;
        result |= add?;
        return result;
        }

    /**
     * The "complement" operator.
     *
     * @return a new set that represents the complement of this set
     *
     * @throws UnsupportedOperation  if this set is incapable of determining its complement, which
     *                               may be a reflection of a limitation of the Element itself
     */
    @Op("~")
    Set! complement()
        {
        // TODO default implementation should just create a Set that answers the opposite of what
        //      this set answers for all the "contains" etc. operations
        throw new UnsupportedOperation();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int count = &this.actualClass.name.size + 2 + 2 * size;
        if (Element.is(Type<Stringable>))
            {
            for (Element e : this)
                {
                count += e.estimateStringLength();
                }
            }
        else
            {
            count += 5 * size; // guess poorly
            }

        return size;
        }

    @Override
    void appendTo(Appender<Char> buf)
        {
        &this.actualClass.name.appendTo(buf);
        buf.add('{');
        if (Element.is(Type<Stringable>))
            {
            Loop: for (Element e : this)
                {
                if (!Loop.first)
                    {
                    buf.addAll(", ");
                    }
                e.appendTo(buf);
                }
            }
        else
            {
            Loop: for (Element e : this)
                {
                if (!Loop.first)
                    {
                    buf.addAll(", ");
                    }
                buf.addAll(e.toString());
                }
            }
        buf.add('}');
        }
    }
