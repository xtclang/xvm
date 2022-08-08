/**
 * A [Map] that is able to create an empty instance of its same implementation, and a copy of
 * itself including all of its contents, should implement this interface.
 */
interface CopyableMap<Key, Value>
        extends Map<Key, Value>
        extends Duplicable
    {
    /**
     * @param transform  an optional key and value transformer
     */
    @Override
    CopyableMap duplicate(function (Key, Value)(Key, Value)? transform = Null);

    /**
     * An mix-in implementation of [CopyableMap] that requires the underlying [Map] to be
     * [Replicable].
     */
    static mixin ReplicableCopier<Key, Value>
            into Replicable + Map<Key, Value>
            implements CopyableMap<Key, Value>
        {
        /**
         * @param transform  an optional element transformer
         */
        @Override
        ReplicableCopier duplicate(function (Key, Value)(Key, Value)? transform = Null)
            {
            if (this.is(immutable CopyableMap) && transform == Null)
                {
                return this;
                }

            if (transform == Null)
                {
                return this.new(this);
                }

            ReplicableCopier<Key, Value> that = this.new();
            for ((Key key, Value value) : this)
                {
                (key, value) = transform(key, value);
                that = that.put(key, value);
                }
            return that;
            }

        @Override
        ReplicableCopier clear()
            {
            return inPlace ? super() : this.new();
            }
        }
    }