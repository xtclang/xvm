/**
 * Implementations of [Collection] that need to implement [Freezable] can use this mix-in to do so:
 *
 *     incorporates conditional CollectionFreezer<Element extends Shareable>
 */
mixin CollectionFreezer<Element extends Shareable>
        into Collection<Element> + CopyableCollection<Element>
        implements Freezable {

    @Override
    immutable CollectionFreezer freeze(Boolean inPlace = False) {
        // don't freeze the Collection if it is already frozen
        if (this.is(immutable)) {
            return this;
        }

        // if the only thing not frozen is the Collection itself, then just make it immutable
        if (inPlace && all(e -> e.is(immutable | service))) {
            return this.makeImmutable();
        }

        // otherwise, just duplicate the Collection, freezing each item as necessary
        return duplicate(e -> frozen(e)).makeImmutable();
    }
}
