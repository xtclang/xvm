import json.Mapping;
import json.Lexer;
import json.Lexer.Token;

import model.DBObjectInfo;


/**
 * The disk storage implementation for a database "single value".
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
     * The oldest transaction ID to keep.
     */
    protected/private Int cutoff = TxManager.USE_LAST_TX;

    /**
     * A linked list node for transaction/value pairs.
     */
    static class TxRecord<Value extends immutable Const>(Int txId, Indicator|Value value, TxRecord? prev=Null);

    /**
     * The
     */
    protected TxRecord<Value>? last = Null;

    /**
     * When true, it indicates that the persistent storage contains expired transactions.
     */
    protected Boolean dirty = False;

    // -----

    /**
     * Obtain the singleton value as it existed immediately after the specified transaction finished
     * committing.
     *
     * @param txId  specifies the transaction identifier to use to determine the point-in-time data
     *              stored in the database, as if the value of the singleton were read immediately
     *              after that specified transaction had committed
     *
     * @return the value of the singleton at the completion of the specified transaction
     */
    Value load(Client.Worker worker, Int txId)
        {
        TODO
        // read bytes
        // wrap as UTF8 reader
        // deserialize
        // return
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the transaction being committed
     * @param value  the new value for the singleton
     */
    void store(Client.Worker worker, Int txId, Value value)
        {
        TODO
        }


    // ----- IO handling ---------------------------------------------------------------------------

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
    void forgetOlderThan(Int tx, Boolean force = False)
        {
        TODO
        }

// TODO
//String loadFile()
//Int[] extractTxList(...)
//String loadTxVal()
//String or Token[] for given tx id
//
//save file from String

    // ----- internal transaction cache ------------------------------------------------------------


    FutureVar<Byte[]>? readOp = Null;

    protected TxRecord ensureTxRecord()
        {
        TODO FutureVar waitForCompletion()
        }

    Int inFlightWrites = 0;

    // TODO void flushTxRecord
    }
