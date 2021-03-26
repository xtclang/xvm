/**
 * Simple Collection implementations that need to implement Freezable can use this mix-in to do so:
 *
 *     incorporates conditional MapFreezer<Key extends immutable Object, Value extends ImmutableAble>
 */
mixin MapFreezer<Key   extends immutable Object,
                 Value extends ImmutableAble>
        into CopyableMap<Key, Value>
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

        if (inPlace && this.inPlace)
            {
            for (Entry entry : this)
                {
                Value v = entry.value;
                if (!v.is(immutable Object))
                    {
                    entry.value = v.freeze();
                    }
                }
            return makeImmutable();
            }

        return duplicate((k, v) -> (k, v.is(immutable Value) ? v : v.freeze())).makeImmutable();
        }
    }
