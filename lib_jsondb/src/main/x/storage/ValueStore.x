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
     * The oldest transaction ID to keep. TODO move up, add "retain set"
     */
    protected/private Int cutoff = Int.minvalue;

    /**
     * A linked list node for transaction/value pairs.
     */
    static class TxRecord<Value extends immutable Const>(Int txId, Indicator|Value value);

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
     * @param txId    the "write" transaction identifier
     * @param worker  a worker to handle CPU-intensive serialization and deserialization tasks
     *
     * @return the value of the singleton as of the specified transaction
     */
    Value load(Int txId, Client.Worker worker)
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
     * @param txId    the "write" transaction identifier
     * @param worker  a worker to handle CPU-intensive serialization and deserialization tasks
     * @param value   the new value for the singleton
     */
    void store(Int txId, Client.Worker worker, Value value)
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
    void retainTx(Set<Int> inUseTxIds, Boolean force = False)
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
