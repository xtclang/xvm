/**
 * Simple List implementations that need to implement Freezable can use this mix-in to do so:
 *
 *     incorporates conditional ListFreezer<Element extends immutable Object | Freezable>
 */
mixin ListFreezer<Element extends ImmutableAble>
        into List<Element> + CopyableCollection<Element>
        implements Freezable
    {
    @Override
    immutable ListFreezer freeze(Boolean inPlace = False)
        {
        if (this.is(immutable ListFreezer))
            {
            return this;
            }

        if (inPlace && all(e -> e.is(immutable Object)))
            {
            return this.makeImmutable();
            }

        // inPlace  inPlace  indexed
        // request  List     List     description
        // -------  -------  -------  --------------------------------------------------------------
        //    N        N        N     these two will copy-construct the frozen contents of this list
        //    N        N        Y
        //
        //    N        Y        N     these two could just copy-construct this list, and freeze that
        //    N        Y        Y     list in place, but instead just do the same as above
        //
        //    Y        N        N     the request is in-place, but list is not, so these ends up
        //    Y        N        Y     being the same as "NNN" and "NNY"
        //
        //    Y        Y        N     easy and efficient: freeze element in place using a cursor
        //    Y        Y        Y     easy and efficient: freeze the elements in place using []

        if (inPlace && this.inPlace)
            {
            if (indexed)
                {
                for (Int i = 0, Int c = size; i < c; ++i)
                    {
                    Element e = this[i];
                    if (!e.is(immutable Object))
                        {
                        this[i] = e.freeze();
                        }
                    }
                }
            else
                {
                Cursor cur = cursor();
                while (cur.exists)
                    {
                    Element e = cur.value;
                    if (!e.is(immutable Object))
                        {
                        cur.value = e.freeze();
                        }
                    cur.advance();
                    }
                }
            return makeImmutable();
            }

        return duplicate(e -> e.is(immutable Element) ? e : e.freeze()).makeImmutable();
        }
    }
