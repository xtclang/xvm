/**
 * Simple Collection implementations that need to implement Freezable can use this mix-in to do so:
 *
 *     incorporates conditional MapFreezer<Key extends immutable Object, Value extends ImmutableAble>
 */
mixin MapFreezer<Key   extends immutable Object,
                 Value extends ImmutableAble>
        into Map<Key, Value>
        implements Freezable
    {
    @Override
    immutable MapFreezer freeze(Boolean inPlace = False)
        {
        if (this.is(immutable MapFreezer))
            {
            return this;
            }

        if (inPlace && values.all(e -> e.is(immutable Object)))
            {
            return makeImmutable();
            }

        if (inPlace && this.inPlace)
            {
            for (val entry : entries)
                {
                Value v = entry.value;
                if (!(v.is(immutable Object)))
                    {
                    entry.value = v.freeze();
                    }
                }
            return makeImmutable();
            }

        // TODO CP this is wrong and requires some logic similar to ListFreezer's
        MapFreezer result = this;
        for ((Key k, Value v) : this)
            {
            if (!(v.is(immutable Object)))
                {
                result = result.put(k, v.freeze());
                }
            }
        return makeImmutable();
        }
    }
