/**
 * Implementations of [Map] that need to implement [Freezable] can use this mix-in to do so:
 *
 *     incorporates conditional MapFreezer<Key extends immutable Object, Value extends Shareable>
 */
mixin MapFreezer<Key   extends immutable Object,
                 Value extends Shareable>
        into CopyableMap<Key, Value>
        implements Freezable
    {
    @Override
    immutable MapFreezer freeze(Boolean inPlace = False)
        {
        // don't freeze the map if it is already frozen
        if (this.is(immutable MapFreezer))
            {
            return this;
            }

        // if the only thing not frozen is the map itself, then just make it immutable
        if (values.all(e -> e.is(immutable Object)))  // TODO CP (immutable|service)
            {
            return inPlace
                    ? makeImmutable()
                    : duplicate().makeImmutable();
            }

        // freeze a map in-place by freezing its values (its keys must already be frozen)
        if (inPlace && this.inPlace)
            {
            for (val entry : entries)
                {
                if (Value+Freezable v := requiresFreeze(entry.value))
                    {
                    entry.value = v.freeze();
                    }
                }
            return makeImmutable();
            }

        // otherwise, just duplicate the map, freezing each value as necessary
        return duplicate((k, v) -> (k, frozen(v))).makeImmutable();
        }
    }
