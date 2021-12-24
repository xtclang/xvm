/**
 * Implementations of [List] that need to implement [Freezable] can use this mix-in to do so:
 *
 *     incorporates conditional ListFreezer<Element extends immutable Object | Freezable>
 */
mixin ListFreezer<Element extends Shareable>
        into List<Element> + CopyableCollection<Element>
        implements Freezable
    {
    @Override
    immutable ListFreezer freeze(Boolean inPlace = False)
        {
        // don't freeze the list if it is already frozen
        if (this.is(immutable ListFreezer))
            {
            return this;
            }

        // if the only thing not frozen is the list itself, then just make it immutable
        if (inPlace && all(e -> e.is(immutable Object)))  // TODO CP (immutable|service)
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
                    if (Element+Freezable e := requiresFreeze(this[i]))
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
                    if (Element+Freezable e := requiresFreeze(cur.value))
                        {
                        cur.value = e.freeze();
                        }
                    cur.advance();
                    }
                }
            return makeImmutable();
            }

        // otherwise, just duplicate the list, freezing each item as necessary
        return duplicate(e -> frozen(e)).makeImmutable();
        }
    }
