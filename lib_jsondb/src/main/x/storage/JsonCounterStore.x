import model.DBObjectInfo;

import json.mapping.IntNumberMapping;

/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
service JsonCounterStore(Catalog catalog, DBObjectInfo info)
        extends JsonValueStore<Int>(catalog, info, JSON_MAPPING, 0)
        implements CounterStore
    {
    static IntNumberMapping<Int> JSON_MAPPING = new IntNumberMapping<Int>();

    // TODO override data structures to allow for blind relative updates, keep track of reads vs writes,
    // TODO override prepare for concurrency optimizations

//    @Override
//    void adjustBlind(Int txId, Int delta = 1)
//        {
//        assert ready();
//        TODO
//        }
    }
