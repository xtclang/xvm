/**
 * A [Collection] that is able to create an empty instance of its same implementation, and a copy of
 * itself including all of its contents, should implement this interface.
 */
interface CopyableCollection<Element>
        extends Collection<Element>
        extends Duplicable
    {
    /**
     * @param transform  an optional element transformer
     */
    @Override
    CopyableCollection duplicate(function Element(Element)? transform = Null);

    /**
     * An mix-in implementation of [CopyableCollection] that requires the underlying [Collection] to
     * be [Replicable].
     */
    static mixin ReplicableCopier<Element>
            into Replicable + Collection<Element>
            implements CopyableCollection<Element>
        {
        /**
         * @param transform  an optional element transformer
         */
        @Override
        ReplicableCopier duplicate(function Element(Element)? transform = Null)
            {
            if (this.is(immutable ReplicableCopier) && transform == Null)
                {
                return this;
                }

            if (transform == Null)
                {
                return this.new(this);
                }

            ReplicableCopier<Element> that = this.new();
            this.map(transform, that);
            return that;
            }

        @Override
        ReplicableCopier clear()
            {
            return inPlace ? super() : this.new();
            }
        }
    }
