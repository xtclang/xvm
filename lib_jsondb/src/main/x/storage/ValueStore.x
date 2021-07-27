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
     * Cached transaction/value pairs.
     */
    protected SkiplistMap<Int, Value> history = new SkiplistMap();


    // ----- storage API exposed to the client -----------------------------------------------------

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing.
     *
     * @param txId  the "write" transaction identifier
     *
     * @return the value of the singleton as of the specified transaction
     */
    Value load(Int txId)
        {
        return currentValue(checkTx(txId));
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the "write" transaction identifier
     * @param value  the new value for the singleton
     */
    void store(Int txId, Value value)
        {
        Changes tx = checkTx(txId);

        tx.value    = value;
        tx.modified = True;
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override PrepareResult prepare(Int writeId, Int prepareId)
        {
        // the transaction can be prepared if (a) no transaction has modified this value after the
        // read id, or (b) the "current" value is equal to the read id transaction's value
        Changes tx = checkTx(writeId);
        if (!tx.modified)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        Value value = tx.value;
        Value prev  = previousValue(tx);

        assert Int latestId := history.last();
        if (latestId == tx.readId)
            {
            if (value == prev)
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }
        else
            {
            Value latest = latestValue();
            if (latest != prev)
                {
                // the state that this transaction assumes as its starting point was altered, so the
                // transaction must roll back
                inFlight.remove(writeId);
                return FailedRolledBack;
                }

            if (value == latest)
                {
                inFlight.remove(writeId);
                return CommittedNoChanges;
                }
            }

        // there is a change to this ValueStore in this transaction, and there has not been an
        // interleaving transaction that invalidates this transaction, so the prepare registers the
        // new value into the historical record for the transaction that is being prepared
        mergePrepare(writeId, prepareId);
        return Prepared;
        }

    @Override Boolean mergePrepare(Int writeId, Int prepareId)
        {
        Boolean modified = False;
        Changes tx       = checkTx(writeId); // TODO allow it to be missing
        if (tx.modified)
            {
            modified = True;
            history.put(prepareId, tx.value);
            tx.modified = False;    // the "changes" no longer differs from the historical record
            }

        tx.readId   = prepareId;    // slide the readId forward to the point that we just prepared
        tx.prepared = True;         // remember that the changed the readId to the prepareId
        return modified;
        }

    @Override Doc commit(Int writeId)
        {
        assert isWriteTx(writeId);

        if (Changes tx := inFlight.get(writeId))
            {
            // TODO write to disk
            TODO
            }

        return Null;
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

    /**
     * Validate the transaction.
     *
     * @param writeId  the transaction id
     *
     * @return the Changes record for the transaction
     */
    @Override Changes checkTx(Int writeId)
        {
        // REVIEW why is this being done here? shouldn't it be done proactively and earlier? (instead of lazily?)
        if (history.empty)
            {
            loadInitial(writeId);
            }

        return super(writeId);
        }

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
                : previousValue(tx);
        }

    /**
     * Obtain the original value from when the transaction began.
     *
     * @param tx  the transaction's Changes record
     *
     * @return the previous value
     */
    protected Value previousValue(Changes tx)
        {
        Int readId = tx.readId;
        while (isWriteTx(readId))
            {
            tx = checkTx(readId);
            if (tx.modified)
                {
                return tx.value;
                }
            readId = tx.readId;
            }


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
