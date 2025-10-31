import json.Doc;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.Parser;

import model.DboInfo;

import TxManager.NO_TX;


/**
 * Provides a key/value storage service for JSON formatted data on disk.
 *
 * The disk format follows this style: (possible multiple line per transaction)
 *
 *     [
 *     {"tx":14, "k":{...}, "v":{...}},
 *     {"tx":17, "k":{...}},
 *     {"tx":18, "k":{...}, "v":{...}}
 *     {"tx":18, "k":{...}, "v":{...}}
 *     ]
 *
 * where a "k" (key) without a corresponding "v" (value) indicates a deletion and the "[...]" part
 * is what sealPrepare() will have returned.
 */
@Concurrent
service JsonMapStore<Key extends immutable Const, Value extends immutable Const>
        extends ObjectStore
        implements MapStore<Key, Value>
        incorporates KeyBasedStore<Key> {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog        catalog,
              DboInfo        info,
              Mapping<Key>   keyMapping,
              Mapping<Value> valueMapping,
              ) {
        construct ObjectStore(catalog, info);

        this.jsonSchema   = catalog.jsonSchema;
        this.keyMapping   = keyMapping;
        this.valueMapping = valueMapping;
        this.rowMapping   = this.jsonSchema.ensureMapping(DataFileRow<Key, Value>);
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * A cached reference to the JSON schema.
     */
    public/protected json.Schema jsonSchema;

    /**
     * The JSON `Mapping` for the keys in the Map.
     */
    public/protected Mapping<Key> keyMapping;

    /**
     * The JSON `Mapping` for the values in the Map.
     */
    public/protected Mapping<Value> valueMapping;

    /**
     * The JSON `Mapping` for a `DataFileRow<Key, Value>`.
     */
    protected Mapping<DataFileRow<Key, Value>> rowMapping;

    /**
     * Used internally within the in-memory MapStore data structures to represent a deleted
     * key/value pair.
     */
    protected enum Deletion {Deleted}

    /**
     * A type representing a value stored in this map.
     *
     * A value could be an actual `Value` or a `Deletion` or a value stored on disc.
     */
    typedef Value | Deletion | DiscStorage as MapValue;

    /**
     * Used as a "singleton" empty map.
     */
    protected immutable OrderedMap<Key, MapValue> NoChanges =
            new SkiplistMap<Key, MapValue>().makeImmutable();

    @Concurrent
    @Override
    protected class Changes {
        @Override
        construct(Int writeId, Future<Int> pendingReadId) {
            super(writeId, pendingReadId);
        }

        /**
         * A map of inserted and updated key/value pairs.
         */
        OrderedMap<Key, MapValue>? mods;

        /**
         * @return a map used to view previously collected modifications, but not intended to be
         *         modified by the caller
         */
        OrderedMap<Key, MapValue> peekMods() {
            return mods ?: NoChanges;
        }

        /**
         * @return the read/write map used to collect modifications
         */
        OrderedMap<Key, MapValue> ensureMods() {
            return mods ?: {
                val map = new SkiplistMap<Key, MapValue>();
                mods = map;
                return map;
            };
        }

        /*
         * When the transaction is sealed (or after it is sealed, but before it commits), the
         * changes in the transaction are rendered for the transaction log, and for storage on disk.
         */
        Map<Key, String>? jsonEntries;
    }

    @Override
    protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * An type that represents an entry in the DBMap JSON data file.
     */
    static const DataFileRow<K, V>(Int tx, K k, V? v = Null);

    /**
     * Cached key/transaction/value triples. This is "the database", in the sense that this is the same
     * data that is stored on disk.
     */
    typedef SkiplistMap<Int, MapValue> as History;
    protected Map<Key, History> history = new HashMap();

    /**
     * A record of how all persistent transactions are laid out on disk. It is computed in terms of
     * range positions of Chars (not bytes).
     */
    typedef Map<Key,    Range<Int>>  as EntryLayout;
    typedef Map<String, EntryLayout> as FileLayout; // very often - just a single key per file
    protected SkiplistMap<Int, FileLayout> storageLayout = new SkiplistMap();

    /**
     * The append offset, measured in Chars, within the each data file, keyed by the Key's URI form
     * (it is basically the length of the file in chars - 2).
     */
    protected Map<String, Int> storageOffset = new HashMap();

    /**
     * Cached map sizes, keyed by transaction id.
     */
    protected SkiplistMap<Int, Int> sizeByTx = new SkiplistMap();

    /**
     * Uncommitted transaction information, held temporarily by prepareId. Basically, while a
     * transaction is being prepared, up until it is committed, the information from [Changes.mods]
     * is copied here, so that a view of the transaction as a separate set of changes is not lost;
     * that information is required by the [commit] processing.
     */
    protected SkiplistMap<Int, OrderedMap<Key, MapValue>> modsByTx = new SkiplistMap();

    /**
     * The ID of the latest known commit for this ObjectStore.
     */
    public/protected Int lastCommit = NO_TX;

    /**
     * Set of file names that contain transactions that could now be safely deleted.
     */
    public/protected Set<String> cleanupPending = new HashSet();

    // ----- storage API exposed to the client -----------------------------------------------------

    @Override
    Int sizeAt(Int txId) {
        checkRead();

        // the adjustments to the size of the transaction will be implied by what is in the Changes
        // record for the transaction, assuming that the transaction is a write ID; otherwise the
        // size is cached by transaction ID in the sizeByTx map
        if (Changes tx := checkTx(txId)) {
            Int readId = tx.readId;
            Int size   = sizeAt(readId);
            for ((Key key, MapValue value) : tx.peekMods()) {
                if (value.is(Deletion)) {
                    --size;
                } else if (!existsAt(readId, key)) {
                    ++size;
                }
            }
            return size;
        }

        assert isReadTx(txId);
        if (model != Empty, Int txFloor := sizeByTx.floor(txId)) {
            assert Int size := sizeByTx.get(txFloor);
            return size;
        }

        return 0;
    }

    @Override
    Boolean existsAt(Int txId, Key key) {
        while (Changes tx := checkTx(txId)) {
            if (MapValue value := tx.peekMods().get(key)) {
                return value != Deleted;
            }

            txId = tx.readId;
        }

        assert isReadTx(txId);
        switch (model) {
        case Empty:
        case Small..Medium:
            // the entire MapStore is cached in the history map
            if (History valueHistory := history.get(key), Int txFloor := valueHistory.floor(txId)) {
                assert MapValue value := valueHistory.get(txFloor);
                return !value.is(Deletion);
            }
            return False;

        case Large:
            TODO
        }
    }

    @Override
    (Key[] keys, immutable Const? cookie) keysAt(Int txId, immutable Const? cookie = Null) {
        if (cookie != Null) {
            TODO
        }

        Int size = sizeAt(txId);
        if (size == 0) {
            return ([], Null);
        }

        updateReadStats();

        switch (model) {
        case Empty:
        case Small..Medium:
            // all the keys and values are in memory; just ship all the keys back in one array
            Key[]   keys        = new Key[](size);
            Int     readId      = txId;
            val     histEntries = history.entries.iterator();
            WriteTx: if (Changes tx := checkTx(txId)) {
                readId = tx.readId;
                if (tx.peekMods().empty) {
                    break WriteTx;
                }

                // complicated: keep an iterator of the changes to merge into the iterator of
                // the underlying (readId) transaction version
                val modEntries = tx.ensureMods().entries.iterator();
                assert var modEntry := modEntries.next();

                // create an iterator of the keys in the history to use as the "main" iterator
                NextKey: while (True) {
                    if (val histEntry := histEntries.next()) {
                        while (True) {
                            // determine if we are at a junction point between the history and
                            // the transactional modifications
                            switch (histEntry.key <=> modEntry.key) {
                            case Lesser:
                                // this is the common case: lots more keys in the history
                                // than in a given transaction
                                History valueHistory = histEntry.value;
                                if (Int txFloor := valueHistory.floor(readId),
                                        MapValue value := valueHistory.get(txFloor),
                                        !value.is(Deletion)) {
                                    keys += histEntry.key;
                                }
                                continue NextKey;

                            case Equal:
                                if (!modEntry.value.is(Deletion)) {
                                    keys += modEntry.key; // i.e. same as histEntry.key
                                }

                                if (modEntry := modEntries.next()) {
                                    continue NextKey;
                                } else {
                                    // we have exhausted the transaction's modifications;
                                    // just break out and drain the remainder of the keys
                                    // in the map history
                                    break NextKey;
                                }

                            case Greater:
                                // the mod appears to be an insert
                                if (!modEntry.value.is(Deletion)) {
                                    keys += modEntry.key;
                                }

                                if (modEntry := modEntries.next()) {
                                    break; // do NOT go to NextKey
                                } else {
                                    // we have exhausted the transaction's modifications;
                                    // just break out and drain the remainder of the keys
                                    // in the map history
                                    break NextKey;
                                }
                            }
                        }
                    } else {
                        // we have exhausted the history, so drain the remainder of the mods
                        do {
                            if (!modEntry.value.is(Deletion)) {
                                keys += modEntry.key;
                            }
                        } while (modEntry := modEntries.next());

                        break NextKey;
                    }
                }
            }

            // take whatever keys remain in the history iterator
            for (val histEntry : histEntries) {
                History valueHistory = histEntry.value;

                if (Int txFloor := valueHistory.floor(readId),
                        MapValue value := valueHistory.get(txFloor),
                        !value.is(Deletion)) {
                    keys += histEntry.key;
                }
            }

            assert keys.size == size;
            return keys.freeze(inPlace=True), Null;

        case Large:
            TODO
        }
    }

    @Override
    conditional Value load(Int txId, Key key) {
        updateReadStats();
        while (Changes tx := checkTx(txId)) {
            if (MapValue value := tx.peekMods().get(key)) {
                return valueFrom(txId, key, value);
            }
            txId = tx.readId;
        }

        assert isReadTx(txId);
        switch (model) {
        case Empty:
        case Small:
            // the entire MapStore is cached in the history map
            if (History valueHistory := history.get(key), Int txFloor := valueHistory.floor(txId)) {
                assert MapValue value := valueHistory.get(txFloor);
                return valueFrom(txId, key, value);
            }
            return False;

        case Medium:
            return loadValueFromDisc(txId, key);

        case Large:
            TODO
        }
    }

    @Override
    void store(Int txId, Key key, Value value) {
        storeImpl(txId, key, value);
    }

    @Override
    void delete(Int txId, Key key) {
        storeImpl(txId, key, Deletion.Deleted);
    }

    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    PrepareResult prepare(Int writeId, Int prepareId) {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        if (tx.peekMods().empty) {
            inFlight.remove(writeId);
            return CommittedNoChanges;
        }

        // obtain the transaction modifications (note: we already verified that modifications exist)
        OrderedMap<Key, MapValue> mods = tx.mods ?: assert;

        // first, we need to verify that there are no conflicts, before we attempt to move the data
        // into the "prepareId" slot in the history
        Int readId = tx.readId;
        if (readId != prepareId - 1) {
            // interleaving transactions have occurred
            for ((Key key, MapValue value) : mods) {
                if (History valueHistory := history.get(key)) {
                    assert Int latestTx := valueHistory.last(), latestTx < prepareId;
                    if (latestTx > readId) {
                        assert MapValue latest := valueHistory.get(latestTx);

                        MapValue prev;
                        if (Int prevTx := valueHistory.floor(readId)) {
                            assert prev := valueHistory.get(prevTx);
                        } else {
                            // the key did not exist in the readId transaction
                            prev = Deleted;
                        }

                        if (&prev != &latest) {
                            // the state that this transaction assumes as its starting point was
                            // altered, so the transaction must roll back
                            inFlight.remove(writeId);
                            return FailedRolledBack;
                        }
                    }
                }
            }
        }

        // now that we have verified that there are no conflicts, the changes need to be "re-homed"
        // into the prepareId transaction in the history, leaving the writeId empty
        Boolean changed = False;
        Int     size    = sizeAt(prepareId-1);
        for ((Key key, MapValue value) : mods) {
            // Determine the actual value to store in the history based on the current model
            MapValue storeValue = model <= Small ? value : OnDisc;

            if (History valueHistory := history.get(key)) {
                assert Int      latestTx := valueHistory.last();
                assert MapValue latest   := valueHistory.get(latestTx);

                switch (latest.is(Deletion), value.is(Deletion)) {
                case (False, False):
                    if (&value != &latest) {

                        valueHistory.put(prepareId, storeValue);
                        changed = True;
                    }
                    break;

                case (False, True):
                    valueHistory.put(prepareId, storeValue);
                    changed = True;
                    --size;
                    break;

                case (True, False):
                    valueHistory.put(prepareId, storeValue);
                    changed = True;
                    ++size;
                    break;

                case (True, True):
                    // technically, this should not be possible, but we're deleting something
                    // that doesn't exist, so the deletion modification has no effect
                    mods.remove(key);
                    break;
                }
            } else if (value.is(Deletion)) {
                // technically, this should not be possible, but we're deleting something that
                // doesn't exist in the history, so the deletion modification has no effect
                mods.remove(key);
            } else {
                History valueHistory = new SkiplistMap();
                valueHistory.put(prepareId, storeValue);
                history.put(key, valueHistory);
                changed = True;
                ++size;
            }
        }

        if (changed) {
            // TODO CP: when is it a "safe" time to transition from Small to Medium model, etc.
            if (model == Empty) {
                model = Small;
            }
        } else {
            inFlight.remove(writeId);
            return CommittedNoChanges;
        }

        // store off transaction's mods and resulting size
        assert !mods.empty, size >= 0;
        modsByTx.put(prepareId, mods);
        sizeByTx.put(prepareId, size);

        // re-do the write transaction to point to the prepared transaction
        tx.readId   = prepareId;
        tx.prepared = True;
        tx.mods     = Null;
        return Prepared;
    }

    @Override
    MergeResult mergePrepare(Int txId, Int prepareId) {
        MergeResult result;

        Int writeId = writeIdFor(txId);
        if (Changes tx := peekTx(writeId)) {
            OrderedMap<Key, MapValue> oldMods = modsByTx.getOrDefault(prepareId, NoChanges);
            OrderedMap<Key, MapValue> newMods = tx.peekMods();

            switch (!oldMods.empty, !newMods.empty) {
            case (False, False):
                result = CommittedNoChanges;
                break;

            case (True, False):
                result = NoMerge;
                break;

            case (False, True):
                oldMods = new SkiplistMap<Key, MapValue>();
                modsByTx.put(prepareId, oldMods);
                continue;
            case (True, True):
                assert !tx.sealed;

                // TODO GG or CP this is supposed to update both modsByTx and sizeByTx for prepareId
                for ((Key key, MapValue value) : newMods) {
                    if (Value prev := latestValue(key, prepareId-1), &value == &prev) {
                        // this part of the transaction is un-doing itself
                        assert History valueHistory := history.get(key);
                        valueHistory.remove(prepareId);
                        continue;
                    }

                    History valueHistory = history.computeIfAbsent(key, () -> new SkiplistMap());
                    if (model <= Small) {
                        valueHistory.put(prepareId, value);
                    } else {
                        valueHistory.put(prepareId, OnDisc);
                    }
                }

                result = Merged; // TODO GG or CP determine when this should be CommittedNoChanges
                break;
            }

            tx.readId   = prepareId;// slide the readId forward to the point that we just prepared
            tx.prepared = True;     // remember that the changed the readId to the prepareId
            tx.mods     = Null;     // the "changes" no longer differs from the historical record

            if (result == CommittedNoChanges) {
                inFlight.remove(writeId);
                sizeByTx.remove(prepareId);
                modsByTx.remove(prepareId);
            }
        } else {
            assert !modsByTx.contains(prepareId) && !sizeByTx.contains(prepareId);
            // REVIEW should this even be possible? maybe just assert?
            result = CommittedNoChanges;
        }

        return result;
    }

    @Override
    String sealPrepare(Int writeId) {
        private String buildJsonTx(Map<Key, String> jsonEntries) {
            if (jsonEntries.empty) {
                return "[]";
            }

            StringBuffer buf = new StringBuffer();
            buf.add('[');
            for (String jsonEntry : jsonEntries.values) {
                buf.add('{')
                   .append(jsonEntry)
                   .add('}').add(',');
            }
            return buf.truncate(-1).add(']').toString();
        }

        assert Changes tx := checkTx(writeId), tx.prepared;
        if (tx.sealed) {
            return buildJsonTx(tx.jsonEntries ?: assert);
        }

        assert Map<Key, MapValue> mods := modsByTx.get(tx.readId);

        HashMap<Key, String> jsonEntries = new HashMap();
        val                  worker      = tx.worker;

        for ((Key key, MapValue value) : mods) {
            StringBuffer buf = new StringBuffer();

            String jsonK = worker.writeUsing(keyMapping, key);

            buf.append("\"k\":").append(jsonK);

            if (!value.is(Deletion)) {
                buf.append(", \"v\":")
                   .append(worker.writeUsing(valueMapping, value));
            }

            jsonEntries.put(key, buf.toString());
        }

        tx.jsonEntries = jsonEntries;
        tx.sealed      = True;

        return buildJsonTx(jsonEntries);
    }

    @Override
    @Synchronized
    void commit(Int[] writeIds) {
        assert !writeIds.empty;

        Boolean cleanup      = !cleanupPending.empty;
        Int     lastCommitId = NO_TX;

        Map<String, StringBuffer> buffers = new HashMap();
        for (Int writeId : writeIds) {
            // because the same array of writeIds are sent to all of the potentially enlisted
            // ObjectStore instances, it is possible that this ObjectStore has no changes for this
            // transaction
            if (Changes tx := peekTx(writeId)) {
                assert tx.prepared, tx.sealed, Map<Key, String> jsonEntries ?= tx.jsonEntries;

                Int prepareId = tx.readId;

                for ((Key key, String jsonEntry) : jsonEntries) {
                    String fileName = nameForKey(key);

                    if (cleanup && cleanupPending.contains(fileName)) {
                        rebuildFile(fileName);

                        cleanupPending.remove(fileName);
                        cleanup = !cleanupPending.empty;
                    }

                    Int fileOffset = storageOffset.getOrDefault(fileName, 0);

                    StringBuffer buf = buffers.computeIfAbsent(fileName, () -> new StringBuffer());

                    // build the String that will be appended to the disk file; format is:
                    //     {"tx":14, "k":{...}, "v":{...}}
                    Int startOffset = fileOffset + buf.size + 2;
                    buf.append(",\n{\"tx\":")
                       .append(prepareId)
                       .add(',').add(' ')
                       .append(jsonEntry)
                       .add('}');

                    // remember the transaction location
                    FileLayout  fileLayout  = storageLayout.computeIfAbsent(prepareId, () -> new HashMap());
                    EntryLayout entryLayout = fileLayout.computeIfAbsent(fileName, () -> new HashMap());

                    entryLayout.put(key, startOffset ..< fileOffset + buf.size);
                }

                modsByTx.remove(prepareId);

                // remember the id of the last transaction that we process here
                lastCommitId = prepareId;
            }
        }

        if (lastCommitId != NO_TX) {
            for ((String fileName, StringBuffer buf) : buffers) {
                storageOffset.process(fileName, e -> {
                    e.value = (e.exists ? e.value : 0) + buf.size;
                });

                // the JSON for entries data is inside an array, so "close" the array
                buf.add('\n').add(']');

                File file = dataDir.fileFor(fileName);

                // write the changes to disk
                if (file.exists) {
                    // TODO right now this assumes that no manual edits have occurred; must cache "last
                    //      update timestamp" and rebuild file if someone else changed it
                    Byte[] bytes = buf.toString().utf8();
                    file.truncate(-2)
                        .append(bytes);
                    bytesUsed += bytes.size;
                } else {
                    // replace the opening "," with an array begin "["
                    buf[0] = '[';

                    immutable Byte[] bytes = buf.toString().utf8();
                    file.contents = bytes;

                    bytesUsed += bytes.size;
                    filesUsed++;
                }

                // update the stats
                lastModified = file.modified;
            }

            // remember which is the "current" value
            lastCommit = lastCommitId;
        }

        // discard the transactional records
        for (Int writeId : writeIds) {
            inFlight.remove(writeId);
        }
    }

    @Override
    void rollback(Int writeId) {
        if (Changes tx := peekTx(writeId)) {
            if (tx.prepared) {
                Int prepareId = tx.readId;

                // the transaction is already sprinkled all over the history
                assert OrderedMap<Key, MapValue> mods := modsByTx.get(prepareId);
                for (Key key : mods) {
                    if (History valueHistory := history.get(key)) {
                        valueHistory.remove(prepareId);
                    }
                }

                modsByTx.remove(prepareId);
                sizeByTx.remove(prepareId);
            }

            inFlight.remove(writeId);
        }
    }

    @Override
    void retainTx(OrderedSet<Int> inUseTxIds, Boolean force = False) {
        for ((Key key, History valueHistory) : history) {
            String fileName = nameForKey(key);

            function void (Int) discard = txId -> {
                valueHistory.remove(txId);
                sizeByTx    .remove(txId);

                if (FileLayout  fileLayout  := storageLayout.get(txId),
                    EntryLayout entryLayout := fileLayout.get(fileName)) {
                    entryLayout.remove(key);
                    if (entryLayout.empty) {
                        fileLayout.remove(fileName);
                        if (fileLayout.empty) {
                            storageLayout.remove(txId);
                        }
                    }

                    cleanupPending.add(fileName);
                }
            };

            processDiscarded(lastCommit, valueHistory.keys.iterator(), inUseTxIds.iterator(), discard);
        }

        if (force && !cleanupPending.empty) {
            using (new SynchronizedSection()) {
                for (String fileName : cleanupPending) {
                    rebuildFile(fileName);
                }
            }
            cleanupPending.clear();
        }
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Obtain the update-to-date value from the transaction.
     *
     * @param key  the key in the map to obtain the value for
     * @param tx   the transaction's Changes record
     *
     * @return True if the key has a value
     * @return the current value
     */
    protected conditional Value currentValue(Key key, Changes tx) {
        if (MapValue value := tx.peekMods().get(key)) {
            return valueFrom(tx.readId, key, value);
        }

        return latestValue(key, tx.readId);
    }

    /**
     * Obtain the original value from when the transaction began.
     *
     * @param key     the key in the map to obtain the value for
     * @param readId  the transaction id to read from
     *
     * @return True if the key has a value as of the specified readId transaction
     * @return the previous value
     */
    protected conditional Value latestValue(Key key, Int readId) {
        if (History valueHistory := history.get(key), readId := valueHistory.floor(readId)) {
            assert MapValue value := valueHistory.get(readId);
            return valueFrom(readId, key, value);
        }

        return False;
    }

    /**
     * Obtain the latest committed value.
     *
     * @param key  the key in the map to obtain the value for
     *
     * @return True if the key has a value
     * @return the latest value
     */
    protected conditional Value latestValue(Key key) {
        if (History valueHistory := history.get(key)) {
            assert Int readId := valueHistory.last();
            assert MapValue value := valueHistory.get(readId);
            return valueFrom(readId, key, value);
        }

        return False;
    }

    /**
     * Returns the value from the DBMap.
     *
     * @param txId   the transaction id to read from
     * @param key    the key in the map to obtain the value for
     * @param value  the current value
     *
     * @return True iff the value is present in the DBMap or can be loaded from disc
     * @return the value for the specified transaction id and key
     */
    protected conditional Value valueFrom(Int txId, Key key, MapValue value) {
        switch(value.is(_)) {
        case Deletion:
            return False;
        case DiscStorage:
            return loadValueFromDisc(txId, key);
        default:
            return True, value.as(Value);
        }
    }

    /**
     * Returns a value from disc.
     *
     * @param txId   the transaction id to read from
     * @param key    the key in the map to obtain the value for
     *
     * @return True iff the value can be loaded from disc
     * @return the value for the specified transaction id and key
     */
    protected conditional Value loadValueFromDisc(Int txId, Key key) {
        if (FileLayout fileLayout := storageLayout.get(txId)) {
            String fileName = nameForKey(key);
            if (EntryLayout entryLayout := fileLayout.get(fileName)) {
                if (Range<Int> location := entryLayout.get(key)) {
                    File       file      = dataDir.fileFor(fileName);
                    Byte[]     bytes     = file.read(location);
                    String     jsonValue = bytes.unpackUtf8();
                    using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonValue.toReader())) {
                        DataFileRow<Key, Value> row = rowMapping.read(stream.ensureElementInput());
                        Value? v = row.v;
                        if (v.is(Value)) {
                            return  True, v;
                        }
                    }
                }
            }
        }
        return False;
    }

    /**
     * Returns `True` iff the current value for a key is stored on disc
     * rather than in memory.
     */
    protected Boolean isValueOnDisc(Key key) {
        if (History valueHistory := history.get(key)) {
            assert Int readId := valueHistory.last();
            assert MapValue value := valueHistory.get(readId);
            return value.is(DiscStorage);
        }
        return False;
    }

    protected conditional Range<Int> storageLocation(Int txId, Key key) {
        if (FileLayout fileLayout := storageLayout.get(txId)) {
            if (EntryLayout entryLayout := fileLayout.get(nameForKey(key))) {
                return entryLayout.get(key);
            }
        }
        return False;
    }

    // ----- IO operations -------------------------------------------------------------------------

    @Override
    void initializeEmpty() {
        assert model == Empty;
        sizeByTx.put(0, 0);
        lastCommit = 0;
    }

    @Override
    void loadInitial() {
        Int desired = txManager.lastCommitted;
        assert desired != NO_TX && desired > 0;

        private function Int (Map.Entry<Object, Int>) incrementCount(Int delta) {
            return entry -> {
                Int newValue = entry.exists ? delta + entry.value : delta;
                entry.value = newValue;
                return newValue;
            };
        }

        Int totalBytes = 0;
        Int totalFiles = 0;

        for (File file : findFiles()) {
            String                  fileName   = file.name;
            Byte[]                  bytes      = file.contents;
            String                  jsonStr    = bytes.unpackUtf8();
            Boolean                 rebuild    = False;
            Parser                  fileParser = new Parser(jsonStr.toReader());
            Map<Key, Range<Int>>    valueLoc   = new HashMap();
            Map<Key, Range<Int>>    entryLoc   = new HashMap();
            Map<Key, Int>           closestTx  = new HashMap();
            SkiplistMap<Int, Key[]> keysByTx   = new SkiplistMap();

            using (val arrayParser = fileParser.expectArray()) {
                while (!arrayParser.eof) {
                    Int startOffset = arrayParser.peek().start.offset;

                    using (val changeParser = arrayParser.expectObject()) {
                        changeParser.expectKey("tx");

                        Int txId = changeParser.expectInt();
                        if (txId > desired) {
                            // this should not be happening
                            rebuild = True;
                            continue;
                        }

                        Key key;

                        changeParser.expectKey("k");
                        using (ObjectInputStream stream =
                                new ObjectInputStream(jsonSchema, changeParser)) {
                            key = keyMapping.read(stream.ensureElementInput());
                        }

                        fileNames.putIfAbsent(key, fileName);

                        if (Int lastTx := closestTx.get(key)) {
                            rebuild = True;
                            if (txId < lastTx) {
                                // out of order transaction record; ignore
                                continue;
                            }
                            keysByTx.process(lastTx, e -> {
                                assert e.exists;
                                e.value -= key;
                            });
                        }

                        closestTx.put(key, txId);
                        keysByTx.process(txId, e -> {
                            e.value = e.exists ? e.value + key : [key];
                        });

                        if (changeParser.matchKey("v")) {
                            (Token first, Token last) = changeParser.skipDoc();
                            valueLoc.put(key, first.start.offset ..< last.end.offset);
                        } else {
                            valueLoc.remove(key);
                        }

                        Token endToken = changeParser.peek();
                        assert endToken.id == ObjectExit;
                        entryLoc.put(key, startOffset ..< endToken.end.offset);
                    }
                }
            }

            if (valueLoc.empty) {
                // all the keys were removed; remove the storage
                file.delete();
                continue;
            }

            totalFiles++;
            totalBytes += bytes.size;

            StringBuffer buf = new StringBuffer();
            if (rebuild) {
                buf.append("[");
            }

            for ((Int txId, Key[] keys) : keysByTx) {
                for (Key key : keys) {
                    if (Range<Int> valueRange := valueLoc.get(key)) {
                        assert Range<Int> entryRange := entryLoc.get(key);

                        if (rebuild) {
                            Int startOffset = buf.size + 1;

                            buf.add('\n')
                               .append(jsonStr.slice(entryRange))
                               .add(',');

                            entryRange = startOffset ..< buf.size-1;

                            FileLayout  fileLayout  = storageLayout.computeIfAbsent(txId, () -> new HashMap());
                            EntryLayout entryLayout = fileLayout.computeIfAbsent(fileName, () -> new HashMap());
                            entryLayout.put(key, entryRange);
                        }

                        if (model <= Small) {
                            String jsonValue = jsonStr.slice(valueRange);
                            using (ObjectInputStream stream = new ObjectInputStream(jsonSchema, jsonValue.toReader())) {
                                Value value = valueMapping.read(stream.ensureElementInput());

                                History valueHistory = new SkiplistMap();
                                valueHistory.put(txId, value);
                                history.put(key, valueHistory);
                            }
                        } else {
                            History valueHistory = new SkiplistMap();
                            valueHistory.put(txId, OnDisc);
                            history.put(key, valueHistory);
                        }
                    }
                }
            }

            sizeByTx.process(desired, incrementCount(valueLoc.size));

            if (rebuild) {
                String           jsonNew  = buf.truncate(-1).add('\n').add(']').toString();
                immutable Byte[] newBytes = jsonNew.utf8();

                file.contents = newBytes;

                totalBytes += newBytes.size - bytes.size;
                storageOffset.put(fileName, jsonNew.size - 2);
            } else {
                // append position is before the closing "\n]"
                storageOffset.put(fileName, jsonStr.size - 2);
            }
        }

        filesUsed  = totalFiles;
        bytesUsed  = totalBytes;
        lastCommit = desired;
    }

    @Override
    Boolean quickScan() {
        if (super() && model != Empty) {
//            TODO: uncomment when the Medium model is implemented
//            StorageModel quantity = switch (filesUsed) {
//                case 0x00: assert;
//                case 0x0001..0x03FF: Small;
//                case 0x0400..0xFFFF: Medium;
//                default: Large;
//            };
//
//            StorageModel weight = bytesUsed <= 0x03FFFF ? Small : Medium;
//
//            // combine the two measure into the model to actually use
//            model = StorageModel.maxOf(quantity, weight);
            model = Small;
        }

        return True;
    }

    @Override
    @Synchronized
    Boolean recover(SkiplistMap<Int, Token[]> sealsByTxId) {
        Map<String, StringBuffer> recoveredContents;
        Map<String, Int>          lastTxInFile;

        if (!((recoveredContents, lastTxInFile) :=
                recoverContents(sealsByTxId, "k", keyMapping, jsonSchema))) {
            return False;
        }

        for ((Int txId, Token[] tokens) : sealsByTxId) {
            using (val sealParser = new Parser(tokens.iterator())) {
                using (val changeArrayParser = sealParser.expectArray()) {
                    while (!changeArrayParser.eof) {
                        using (val changeParser = changeArrayParser.expectObject()) {
                            String fileName;

                            changeParser.expectKey("k");

                            Token[] keyTokens = changeParser.skip(new Token[]);
                            using (ObjectInputStream stream =
                                    new ObjectInputStream(jsonSchema, keyTokens.iterator())) {
                                Key key = keyMapping.read(stream.ensureElementInput());

                                fileName = nameForKey(key);
                            }

                            // apply just the missing transactions
                            if (Int lastInFile := lastTxInFile.get(fileName),
                                    lastInFile < txId || lastInFile == NO_TX) {
                                assert StringBuffer buf := recoveredContents.get(fileName);

                                appendChange(buf, txId, "k", keyTokens, "v",
                                        changeParser.matchKey("v")
                                            ? changeParser.skip(new Token[])
                                            : []);
                            }
                        }
                    }
                }
            }
        }

        for ((String fileName, StringBuffer buf) : recoveredContents) {
            buf.truncate(-1).add('\n').add(']');
            dataDir.fileFor(fileName).contents = buf.toString().utf8();
        }

        return True;
    }

    // REVIEW something like this? -> protected Boolean storeImpl(Int txId, Key key, MapValue value, Boolean blind)
    protected void storeImpl(Int txId, Key key, MapValue value) {
        assert Changes tx := checkTx(txId, writing=True);
        OrderedMap<Key, MapValue> mods = tx.ensureMods();
        if (MapValue current := mods.get(key)) {
            if (&value != &current) {
                if (value.is(Deletion) && !existsAt(tx.readId, key)) {
                    mods.remove(key);
                } else {
                    mods.put(key, value);
                }
            }
        } else if (!(value.is(Deletion) && !existsAt(tx.readId, key))) {
            mods.put(key, value);
        }
    }

    @Override
    void unload() {
        inFlight.clear();
        modsByTx.clear();
        history.clear();
        storageLayout.clear();
        storageOffset.clear();
        fileNames.clear();
        sizeByTx.clear();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Rebuild the content of the specified file.
     */
    private void rebuildFile(String fileName) {
        StringBuffer buf  = new StringBuffer();
        File         file = dataDir.fileFor(fileName);

        assert file.exists;

        Byte[] oldBytes = file.contents;
        String jsonStr  = oldBytes.unpackUtf8();

        for ((Int txId, FileLayout fileLayout) : storageLayout) {
            if (EntryLayout entryLayout := fileLayout.get(fileName)) {
                for ((Key key, Range<Int> entryRange) : entryLayout) {
                    Int startPos = buf.size + 2;

                    buf.add(',').add('\n')
                       .append(jsonStr.slice(entryRange));

                    entryLayout.put(key, startPos ..< buf.size);
                }
            }
        }

        Int fileOffset = buf.size;
        storageOffset.put(fileName, fileOffset);

        buf[0] = '[';
        buf.add('\n').add(']');

        immutable Byte[] newBytes = buf.toString().utf8();
        file.contents = newBytes;

        bytesUsed   += newBytes.size - oldBytes.size;
        lastModified = file.modified;
    }
}