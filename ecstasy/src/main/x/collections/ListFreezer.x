/**
 * Simple List implementations that need to implement Freezable can use this mix-in to do so:
 *
 *     incorporates conditional ListFreezer<Element extends immutable Object | Freezable>
 */
mixin ListFreezer<Element extends ImmutableAble>
        into List<Element>
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
            return makeImmutable();
            }

        ListFreezer result = this;
        if (inPlace && !indexed)
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
        else
            {
            Loop: for (Element e : this)
                {
                if (!e.is(immutable Object))
                    {
                    result = result.replace(Loop.count, e.freeze());
                    }
                }
            }
        return result.makeImmutable();
        }
    }
