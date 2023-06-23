/**
 * A MapCollector that freezes the result once it has been collected.
 *
 * @param create  the optional Map factory function
 */
const CollectImmutableMap<Key extends Shareable,
                          Value extends Shareable,
                          Result extends immutable Map<Key, Value>>(function Map<Key,Value>()? create = Null)
        extends MapCollector<Key, Value, Result>(create) {

    @Override
    Result reduce(Map<Key, Value> accumulator) {
        return accumulator.is(Freezable)?.freeze(inPlace=True).as(Result) : accumulator.makeImmutable().as(Result);
    }
}