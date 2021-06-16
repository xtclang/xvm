import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

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

    @Override PrepareResult prepare(Int writeId)
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
        if (value == prev)
            {
            inFlight.remove(writeId);
            return CommittedNoChanges;
            }

        assert Int latestId := history.last();
        if (latestId != tx.readId)
            {
            Value latest = latestValue();
            if (latest != prev)
                {
                inFlight.remove(writeId);
                return latest == value ? CommittedNoChanges : FailedRolledBack;
                }
            }

        // there is a change to this ValueStore in this transaction, and there has not been an
        // interleaving transaction that invalidates this transaction
        return Prepared;
        }

    @Override Boolean mergeTx(Int fromTxId, Int toTxId, Boolean release = False)
        {
        TODO
        }

    @Override void commit(Int prepareId, Int commitId)
        {
        TODO
        }

    @Override void rollback(Int uncommittedId)
        {
        assert isWriteTx(uncommittedId);
        TODO
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
    Changes checkTx(Int writeId)
        {
        assert isWriteTx(writeId);

        if (history.empty)
            {
            loadInitial(writeId);
            }

        return inFlight.computeIfAbsent(writeId,
                () -> new Changes(writeId, txManager.enlist(this, writeId)));
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

    @Override
    void retainTx(Set<Int> inUseTxIds, Boolean force = False)
        {
        TODO
        }
    }
