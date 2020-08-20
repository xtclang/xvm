/**
 * A [Collection] that is able to create an empty instance of its same implementation, and a copy of
 * itself including all of its contents, should implement this interface.
 */
interface CopyableCollection<Element>
        extends Collection<Element>
        extends Replicable
        extends Duplicable
    {
    /**
     * @param transform  an optional element transformer
     */
    @Override
    CopyableCollection duplicate(function Element(Element)? transform = Null)
        {
        if (this.is(immutable CopyableCollection) && transform == Null)
            {
            return this;
            }

        if (transform == Null)
            {
            return this.new(this);
            }

        CopyableCollection<Element> that = this.new();
        this.map(transform, that);
        return that;
        }

    @Override
    CopyableCollection clear()
        {
        return inPlace ? super() : this.new();
        }
    }
