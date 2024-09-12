/**
 * `ListMapCollector` is a [MapCollector] that produces a [ListMap], which is optionally persistent
 * (i.e. not [Map.inPlace]).
 *
 * @param makePersistent  (optional) specify `False` (the default) to produce a [ListMap] with
 *                        [Map.inPlace] of `True`, or specify `True` to produce a persistent
 *                        `ListMap`
 */
const ListMapCollector<Key, Value, Result extends ListMap<Key, Value>>(Boolean makePersistent = False)
        extends MapCollector<Key, Value, Result> {
    @Override
    Result reduce(Map<Key, Value> accumulator) {
        Result map = accumulator.as(Result);
        if (makePersistent) {
            map = map.ensurePersistent(True).as(Result);
        }
        return map;
    }
}