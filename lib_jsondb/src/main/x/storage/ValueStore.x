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
    protected static class Changes<Value extends immutable Const>
            (Int writeId, Int readId, Boolean modified = False, Value? value = Null)
        {
        /**
         * This txId, the "write" txId.
         */
        Int writeId;

        /**
         * The read txId that this transaction is based from.
         */
        Int readId;

        /**
         *
         */
        Boolean modified = False;

        /**
         * The value within the transaction.
         */
        Value? value;
        }

    /**
     * Cached transaction/value pairs.
     */
    protected SkiplistMap<Int, Value> history = new SkiplistMap();

    /**
     * In flight transactions.
     */
    protected SkiplistMap<Int, Changes<Value>> inFlight = new SkiplistMap();


    // ----- storage API exposed to the client -----------------------------------------------------

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing.
     *
     * @param txId    the "write" transaction identifier
     * @param worker  a worker to handle CPU-intensive serialization and deserialization tasks
     *
     * @return the value of the singleton as of the specified transaction
     */
    Value load(Int txId, Client.Worker worker)
        {
        Changes<Value> tx = checkTx(txId, worker);

        if (tx.modified)
            {
            return tx.value.as(Value);
            }

        Int readId = tx.readId;
        if (isWriteTx(readId))
            {
            assert readId != txId;
            return load(readId, worker);
            }

        assert Value value := history.get(readId);
        return value;
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId    the "write" transaction identifier
     * @param worker  a worker to handle CPU-intensive serialization and deserialization tasks
     * @param value   the new value for the singleton
     */
    void store(Int txId, Client.Worker worker, Value value)
        {
        Changes<Value> tx = checkTx(txId, worker);

        tx.value    = value;
        tx.modified = True;
        }


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override PrepareResult prepare(Int writeId, Int prepareId)
        {
        TODO
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


    // ----- internal IO handling ------------------------------------------------------------------

    /**
     * Validate the transaction.
     *
     * @param writeId  the transaction id
     * @param worker   a worker to handle CPU-intensive serialization and deserialization tasks
     *
     * @return the Changes record for the transaction
     */
    Changes<Value> checkTx(Int writeId, Client.Worker worker)
        {
        assert isWriteTx(writeId);

        if (history.empty)
            {
            loadInitial(worker);
            }

        return inFlight.computeIfAbsent(writeId,
                () -> new Changes<Value>(writeId, txManager.enlist(this, writeId)));
        }

    /**
     * Load the value(s) from disk.
     *
     * @param worker  a worker to handle CPU-intensive serialization and deserialization tasks
     */
    void loadInitial(Client.Worker worker)
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
