import json.Doc;
import json.Lexer;
import json.Lexer.Token;
import json.Mapping;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database "single value".
 *
 * The disk format follows this style:
 *
 *     [
 *     {"tx":14, "value":{...}},
 *     {"tx":17, "value":{...}},
 *     {"tx":18, "value":{...}}
 *     ]
 */
service ValueStore<Value extends immutable Const>
        extends ObjectStore
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DBObjectInfo     info,
              Appender<String> errs,
              Mapping<Value>   valueMapping,
              Value            initial,
              )
        {
        construct ObjectStore(catalog, info, errs);

        this.valueMapping = valueMapping;
        this.initial      = initial;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON Mapping for the singleton value.
     */
    public/protected Mapping<Value> valueMapping;

    /**
     * The initial singleton value. A singleton always has a value, even when it is newly created
     * (before it has any value stored); this property provides that initial value.
     */
    public/protected Value initial;

    /**
     * The file used to store the data for the DBValue.
     */
    @Lazy File dataFile.calc()
        {
        return dataDir.fileFor("value.json");
        }

    /**
     * An internal, mutable record of Changes for a specific transaction.
     */
    @Override protected class Changes(Int writeId, Int readId)
        {
        /**
         * The value within the transaction; only set if [modified] is set to `True`.
         */
        @Unassigned Value value;
        }

    @Override protected SkiplistMap<Int, Changes> inFlight = new SkiplistMap();

    /**
     * Cached transaction/value pairs. This is "the database", in the sense that this is the same
     * data that is stored on disk.
     */
    protected SkiplistMap<Int, Value> history = new SkiplistMap();


    // ----- storage API exposed to the client -----------------------------------------------------

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing, or as it exists within the transaction (if it has not yet committed).
     *
     * @param txId  the "write" transaction identifier
     *
     * @return the value of the singleton as of the specified transaction
     */
    Value load(Int txId)
        {
        if (Changes tx := checkTx(txId))
            {
            return currentValue(tx);
            }
        else
            {
            return latestValue(txId);
            }
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the "write" transaction identifier
     * @param value  the new value for the singleton
     */
    void store(Int txId, Value value)
        {
        assert Changes tx := checkTx(txId, writing=True), !tx.sealed;
        tx.value    = value;
        tx.modified = True;
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override PrepareResult prepare(Int writeId, Int prepareId)
        {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        assert Changes tx := checkTx(writeId);
        if (!tx.modified)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        Value value  = tx.value;
        Int   readId = tx.readId;
        Value prev   = latestValue(readId);

        assert Int latestId := history.last();
        if (latestId == tx.readId)
            {
            if (&value == &prev) // any change assumed significant, so use reference equality
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }
        else
            {
            Value latest = latestValue();
            if (&latest != &prev) // any change assumed significant, so use reference equality
                {
                // the state that this transaction assumes as its starting point was altered, so the
                // transaction must roll back
                inFlight.remove(writeId);
                return FailedRolledBack;
                }

            if (&value == &latest) // any change assumed significant, so use reference equality
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }

        // there is a change to this ValueStore in this transaction, and there has not been an
        // interleaving transaction that invalidates this transaction, so the prepare registers the
        // new value into the historical record for the transaction that is being prepared
        history.put(prepareId, tx.value);
        tx.readId   = prepareId;
        tx.prepared = True;
        tx.modified = False;
        return Prepared;
        }

    @Override MergeResult mergePrepare(Int writeId, Int prepareId, Boolean seal = False)
        {
        MergeResult result = NoMerge;

        if (Changes tx := peekTx(writeId))
            {
            assert !tx.sealed;
            if (tx.modified)
                {
                Value prev = latestValue(prepareId-1);
                if (tx.&value == &prev)
                    {
                    // the transaction is un-doing itself
                    inFlight.remove(writeId);
                    history.remove(prepareId);
                    result = CommittedNoChanges;
                    }
                else
                    {
                    history.put(prepareId, tx.value);
                    result = Merged;
                    }
                }

            tx.readId   = prepareId;// slide the readId forward to the point that we just prepared
            tx.prepared = True;     // remember that the changed the readId to the prepareId
            tx.modified = False;    // the "changes" no longer differs from the historical record
            tx.sealed   = seal;
            }

        return result;
        }

    @Override OrderedMap<Int, Doc> commit(OrderedMap<Int, Int> writeIdForPrepareId)
        {
TODO
//        assert isWriteTx(writeId);
//
//        if (Changes tx := inFlight.get(writeId))
//            {
//            // TODO write to disk
//            TODO return a doc containing the tx record
//            }
//
//        return Null;
        }

    @Override void rollback(Int writeId)
        {
        assert isWriteTx(writeId);

        // if this ObjectStore has any record of the transaction, then that record needs to be
        // discarded
        inFlight.processIfPresent(writeId, entry ->
            {
            // the transaction may point to a prepared copy of the transaction that has already been
            // placed in the "history" records
            Changes tx = entry.value;
            if (tx.prepared)
                {
                history.remove(tx.readId);
                }

            // dispose of the transaction record
            entry.delete();
            });
        }

    @Override void retainTx(Set<Int> inUseTxIds, Boolean force = False)
        {
        TODO
        }


    // ----- internal ------------------------------------------------------------------------------

// REVIEW where does the "init" logic go?
//    if (history.empty)
//        {
//        loadInitial(writeId);
//        }

    /**
     * Obtain the update-to-date value from the transaction.
     *
     * @param tx  the transaction's Changes record
     *
     * @return the current value
     */
    protected Value currentValue(Changes tx)
        {
        return tx.modified
                ? tx.value
                : latestValue(tx.readId);
        }

    /**
     * Obtain the original value from when the transaction began.
     *
     * @param readId  the transaction id to read from
     *
     * @return the previous value
     */
    protected Value latestValue(Int readId)
        {
        assert readId := history.floor(readId);
        assert Value value := history.get(readId);
        return value;
        }

    /**
     * Obtain the latest committed value.
     *
     * @return the latest value
     */
    protected Value latestValue()
        {
        assert Int   readId := history.last();
        assert Value latest := history.get(readId);
        return latest;
        }


    // ----- IO operations -------------------------------------------------------------------------

    /**
     * Load the value(s) from disk.
     *
     * @param writeId  the transaction that triggered the initial load from disk
     */
    void loadInitial(Int writeId)
        {
        using (new CriticalSection())
            {
            Value value;
            Int   loadId = txManager.lastClosedId;
            if (dataFile.exists)
                {
                TODO // value =
                }
            else
                {
                value = initial;
                }
            history.put(loadId, value);
            }
        }

    @Override
    Iterator<File> findFiles()
        {
        File file = dataFile;
        return (file.exists ? [file] : []).iterator();
        }

    @Override
    Boolean deepScan(Boolean fix = True)
        {
        TODO
        }

    @Override
    Boolean quickScan()
        {
        TODO
        }
    }
