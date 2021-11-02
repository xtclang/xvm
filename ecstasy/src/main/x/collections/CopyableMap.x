/**
 * A [Map] that is able to create an empty instance of its same implementation, and a copy of
 * itself including all of its contents, should implement this interface.
 */
@Concurrent
interface CopyableMap<Key, Value>
        extends Map<Key, Value>
        extends Replicable
        extends Duplicable
    {
    /**
     * @param transform  an optional element transformer
     */
    @Override
    CopyableMap duplicate(function (Key, Value)(Key, Value)? transform = Null)
        {
        if (this.is(immutable CopyableMap) && transform == Null)
            {
            return this;
            }

        if (transform == Null)
            {
            return this.new(this);
            }

        CopyableMap<Key, Value> that = this.new();
        for ((Key key, Value value) : this)
            {
            (key, value) = transform(key, value); // TODO GG: inline
            that = that.put(key, value);
            }
        return that;
        }

    @Override
    CopyableMap clear()
        {
        return inPlace ? super() : this.new();
        }
    }
