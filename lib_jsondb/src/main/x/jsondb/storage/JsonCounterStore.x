import model.DboInfo;

import json.mappings.IntNumberMapping;

/**
 * Provides the low-level I/O for a transactional counter, with optimizations for blind adjustments
 * in concurrent transactions.
 */
@Concurrent
service JsonCounterStore(Catalog catalog, DboInfo info)
        extends JsonValueStore<Int>(catalog, info, JSON_MAPPING, 0)
        implements CounterStore {

    static IntNumberMapping<Int> JSON_MAPPING = new IntNumberMapping<Int>();

    @Concurrent
    @Override
    protected class Changes {
        @Override
        construct(Int writeId, Future<Int> pendingReadId) {
            super(writeId, pendingReadId);
        }

        /**
         * Tracks whether the transaction had to read the value.
         */
        Boolean didPeek;

        /**
         * Tracks whether the transaction adjusted the value (with or without reading it).
         */
        Boolean didAdjust;

        /**
         * The amount of the adjustment to apply.
         */
        Int adjustment;
    }

    @Override
    Int load(Int txId) {
        Int value = super(txId);

        if (Changes tx := checkTx(txId)) {
            tx.didPeek = True;
            if (tx.adjustment != 0) {
                value += tx.adjustment;
                tx.adjustment = 0;
                store(txId, value);
            }
        }

        return value;
    }

    @Override
    void store(Int txId, Int value) {
        assert Changes tx := checkTx(txId, writing=True);
        super(txId, value);
        tx.adjustment = 0;
    }

    @Override
    void adjustBlind(Int txId, Int delta = 1) {
        // the concurrency optimization is only applicable before we prepare
        if (txCat(txId) == Open) {
            assert Changes tx := checkTx(txId, writing=True);
            tx.adjustment += delta;
            tx.didAdjust   = True;
        } else {
            super(txId, delta);
        }
    }

    @Override
    PrepareResult prepare(Int writeId, Int prepareId) {
        // optimize for blind updates
        assert Changes tx := checkTx(writeId);
        if (!tx.didPeek && (tx.modified || tx.didAdjust)) {
            assert Int latestId := history.last();
            assert Int prev     := history.get(latestId);
            Int value = (tx.modified ? tx.value : prev) + tx.adjustment;
            if (value == prev) {
                inFlight.remove(writeId);
                return CommittedNoChanges;
            }

            tx.value      = value;
            tx.adjustment = 0;
            tx.readId     = prepareId;
            tx.prepared   = True;
            tx.modified   = False;
            history.put(prepareId, value);
            return Prepared;
        } else {
            return super(writeId, prepareId);
        }
    }
}