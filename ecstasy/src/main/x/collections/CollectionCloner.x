/**
 * A deep cloning implementation that can be added to any Collection implementation with the
 * following line:
 *
 *     incorporates conditional CollectionCloner<Element extends Cloneable>
 */
@Concurrent
mixin CollectionCloner<Element extends Cloneable>
        into CopyableCollection<Element>
        implements Cloneable
    {
    @Override
    CollectionCloner clone()
        {
        return this.is(immutable CollectionCloner)
                ? this
                : duplicate(e -> e.clone());
        }
    }
