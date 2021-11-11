import collections.SparseIntSet;

import ecstasy.collections.maps.KeySetBasedMap;
import ecstasy.reflect.Annotation;

import json.Doc;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectOutputStream;

import oodb.DBCounter;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBProcessor;
import oodb.DBProcessor.Pending;
import oodb.DBProcessor.Schedule;
import oodb.DBQueue;
import oodb.DBSchema;
import oodb.DBTransaction;
import oodb.DBUser;
import oodb.DBValue;
import oodb.CommitFailed;
import oodb.RootSchema;
import oodb.SystemSchema;
import oodb.Transaction.CommitResult;
import oodb.Transaction.TxInfo;

import model.DBObjectInfo;

import storage.CounterStore;
import storage.LogStore;
import storage.MapStore;
import storage.ObjectStore;
import storage.ProcessorStore;
import storage.ValueStore;

import Catalog.BuiltIn;
import TxManager.NO_TX;
import TxManager.Requirement;


/**
 * The root of the JSON database API, as exposed to applications. This provides an implementation of
 * the OODB Connection and Transaction interfaces.
 *
 * To minimize the potential for name collisions between the implementations of the Connection and
 * Transaction interfaces, the implementations are nested as virtual children of this service, with
 * no state other than the implicit [outer] property (which is already effectively a reserved name).
 *
 * This service is effectively abstract; it is expected that each actual database (each custom
 * root schema packaged as a module) will have a corresponding generated ("code gen") module that
 * will contain a sub-class of this class. By sub-classing this Client class, the generated code is
 * able to provide a custom, type-safe representation of each custom database, effectively merging
 * together the OODB API with the custom API and the custom type system defined by the database and
 * its schema.
 */
service Client<Schema extends RootSchema>
    {
    /**
     * Construct a Client service, representing a connection to the database and any current
     * transaction.
     *
     * @param catalog        the JSON db catalog, representing the database on disk
     * @param id             the id assigned to this Client service
     * @param dbUser         the database user to create the client on behalf of
     * @param readOnly       (optional) pass True to indicate that client is not permitted to modify
     *                       any data
     * @param notifyOnClose  the function to call when the client connection is closed
     */
    construct(Catalog<Schema>        catalog,
              Int                    id,
              DBUser                 dbUser,
              Boolean                readOnly = False,
              function void(Client)? notifyOnClose = Null)
        {
        assert Schema == RootSchema || catalog.metadata != Null;

        this.id            = id;
        this.dbUser        = dbUser;
        this.catalog       = catalog;
        this.clock         = catalog.clock;
        this.txManager     = catalog.txManager;
        this.jsonSchema    = catalog.jsonSchema;
        this.readOnly      = readOnly || catalog.readOnly;
        this.notifyOnClose = notifyOnClose;
        }
    finally
        {
        // exclusive re-entrancy is critically important: it eliminates race conditions while any
        // operation (including a commit or rollback) is in flight, while still allowing re-entrancy
        // that is required to carry out that operation
        conn   = new Connection(infoFor(0)).as(Connection + Schema);
        worker = new Worker(jsonSchema);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON db catalog, representing the database on disk.
     */
    public/private Catalog<Schema> catalog;

    /**
     * The shared clock for the database.
     */
    public/private Clock clock;

    /**
     * The transaction manager for this `Catalog` object. The transaction manager provides a
     * sequential ordered (non-concurrent) application of potentially concurrent transactions.
     */
    public/private TxManager<Schema> txManager;

    /**
     * A cached reference to the JSON schema.
     */
    public/private json.Schema jsonSchema;

    /**
     * True iff this client was created in read-only mode.
     */
    public/private Boolean readOnly;

    /**
     * The id assigned to this Client service.
     */
    public/private Int id;

    /**
     * The DBUser represented by this Client service.
     */
    public/private DBUser dbUser;

    /**
     * The Connection represented by this Client service. Set to Null when the Connection is closed.
     */
    public/protected (Connection + Schema)? conn = Null;

    /**
     * The current Transaction on the Connection, or Null if no Transaction is active. Set to Null
     * when either the Connection or current Transaction is closed.
     */
    public/protected (Transaction + Schema)? tx = Null;

    /**
     * The lazily created application DBObjects within the schema.
     */
    protected/private DBObjectImpl?[] appObjects = new DBObjectImpl?[];

    /**
     * The lazily created system schema DBObjects.
     */
    protected/private DBObjectImpl?[] sysObjects = new DBObjectImpl?[];

    /**
     * The function to use to notify that the connection has closed.
     */
    protected function void(Client)? notifyOnClose;

    /**
     * A read-only client that provides the "before transaction" view.
     */
    protected/private Client<Schema>? preTxView;

    /**
     * The serialization/deserialization "worker" that allows CPU-intensive work to be dumped back
     * onto this Client from the database.
     */
    @Unassigned public/private Worker worker;


    // ----- support -------------------------------------------------------------------------------

    /**
     * Log a message to the system log.
     *
     * @param msg  the message to log
     */
    protected void log(String msg)
        {
        catalog.log^(msg);
        }

    /**
     * Helper to convert an object to a string, without allowing an exception to be raised by the
     * processing thereof.
     *
     * @param o  an object
     *
     * @return a String
     *
     */
    static protected String safeToString(Object o)
        {
        try
            {
            return o.toString();
            }
        catch (Exception _)
            {
            return "???";
            }
        }

    /**
     * Verify that the client connection is open and can be used for reading data.
     *
     * @return True if the check passes
     *
     * @throws Exception if the check fails
     */
    Boolean checkRead()
        {
        assert conn != Null;
        return True;
        }

    /**
     * Verify that the client connection is open and can be used for changing data.
     *
     * @return True if the check passes
     *
     * @throws Exception if the check fails
     */
    Boolean checkWrite()
        {
        assert !readOnly && !tx?.txInfo.readOnly;
        return checkRead();
        }

    /**
     * Obtain the current transaction, creating one if necessary, and wrapping it in a transactional
     * context.
     *
     * @param dbo  pass the DBObject instance that is requesting the transaction
     *
     * @return the transactional context object
     */
    TxContext ensureTransaction(DBObjectImpl dbo, Boolean allowNontransactional=False, Boolean override=False)
        {
        private TxContext ctx = new TxContext();
        private TxContext ntx = new NtxContext();

        checkRead();

        if (!dbo.transactional && allowNontransactional)
            {
            return ntx;
            }

        var     tx         = this.tx;
        Boolean autocommit = False;

        if (tx == Null)
            {
            assert !internal;

            tx         = (conn?: assert).createTransaction(name="autocommit", override=override);
            this.tx    = tx;
            autocommit = True;
            }

        ctx.enter(autocommit);

        // there is no way to report the error back from this context; the tx is marked rollback
        // only, but the client is in an entirely different call, and not able to receive back
        // and indication of the deferred failure, so it can only determine this failure by
        // checking the rollbackOnly property
        dbo.dboProcessDeferred_();

        return ctx;
        }

    /**
     * For the transaction manager's internal clients that emulate various stages in a transaction,
     * this sets the current transaction id for the client.
     *
     * @param txId  the transaction id to use as a read level
     */
    void representTransaction(Int txId)
        {
        assert internal;

        // update the readOnly property of the Client based on the requested transaction ID
        readOnly = switch(TxManager.txCat(txId))
            {
            case ReadOnly    : True;
            case Open        : False;
            case Validating  : True;
            case Rectifying  : False;
            case Distributing: False;
            };

        // two cached transaction info objects (one R/W, one RO) for internal use
        static TxInfo rwInfo = new TxInfo(id=0, name="internal", readOnly=False);
        static TxInfo roInfo = new TxInfo(id=0, name="internal", readOnly=True );

        TxInfo txInfo = readOnly ? roInfo : rwInfo;
        if (tx == Null)
            {
            // create a transaction to represent the requested transaction ID
            DBObjectInfo dboInfo = infoFor(0); // the root schema
            tx = new Transaction(dboInfo, txInfo, txId).as(Transaction + Schema);
            }
        else
            {
            tx?.represent_(txInfo, txId);
            }
        }

    /**
     * @return the internal Client that represents the version of the database before this
     *         transaction made changes
     */
    Client<Schema> ensurePreTxClient()
        {
        Client<Schema> client;
        if (client ?= preTxView)
            {
            return client;
            }

        assert internal;

        client = txManager.allocateClient();
        client.representTransaction(txManager.readIdFor(tx?.id_ : assert));
        preTxView = client;
        return client;
        }

    /**
     * True if this is an internal ("system") client.
     */
    @RO Boolean internal.get()
        {
        return Catalog.isInternalClientId(id);
        }

    /**
     * On behalf of the scheduler, create a new internal transaction.
     *
     * @param retryCount  the number of times this transaction has been retried (zero indicates the
     *                    first attempt)
     */
    void createProcessTx(Int retryCount)
        {
        assert internal;

        // roll back any previously left-over transaction (unlikely that this would ever occur)
        if (tx != Null)
            {
            rollbackProcessTx();
            }

        assert Connection conn ?= this.conn;
        conn.createTransaction(name="async", retryCount=retryCount, override=True);
        }

    /**
     * Process one pending message. Called by the Scheduler.
     *
     * @param dboId    indicates which DBProcessor
     * @param pid      indicates which Pending
     * @param message  the Message to process
     *
     * @return the elapsed processing time
     * @return the exceptional processing failure, iff the processing failed
     */
    <Message extends immutable Const> (Range<DateTime>, Exception?) processMessage(Int dboId, Int pid, Message message)
        {
        assert internal;

        val dbo = implFor(dboId).as(DBProcessorImpl<Message>);

        return dbo.process_(pid, message);
        }

    /**
     * On behalf of the scheduler, commit the current internal transaction.
     *
     * @return the CommitResult
     */
    CommitResult commitProcessTx()
        {
        assert internal;
        assert Transaction tx ?= this.tx;
        return tx.commit(override=True);
        }

    /**
     * On behalf of the scheduler, roll back the current internal transaction.
     */
    void rollbackProcessTx()
        {
        assert internal;
        if (Transaction tx ?= this.tx)
            {
            tx.rollback(override=True);
            }
        }

    /**
     * Determine if the processing of the specified message should be retried, and signal the
     * abandonment of the message processing if indicated.
     *
     * This method performs its own transaction management; do **not** call this method with a
     * transaction already active.
     *
     * @param dboId           indicates which DBProcessor
     * @param pid      indicates which Pending
     * @param message         the message that failed to be processed and committed
     * @param result          the result from the last failed attempt, which is either a
     *                        commit failure indicated as a [CommitResult], or an `Exception`
     * @param when            the [Schedule] that caused the message to be processed
     * @param elapsed         the period of time consumed by the failed processing of the message
     * @param timesAttempted  the number of times that the processing of this message has been
     *                        attempted, and has failed
     * @param abandoning      True iff the scheduler has already decided to abandon retries of
     *                        processing of the message
     *
     * @return True if the Scheduler indicated that it is abandoning, or if the DBProcessor
     *         indicates it is abandoning retrying of the processing of the message
     */
    <Message extends immutable Const> Boolean processingFailed(
            Int                      dboId,
            Int                      pid,
            Message                  message,
            CommitResult | Exception result,
            Schedule?                when,
            Range<DateTime>          elapsed,
            Int                      timesAttempted,
            Boolean                  abandoning,
            )
        {
        assert internal;

        // roll back any previously left-over transaction (unlikely, but just in case)
        if (tx != Null)
            {
            rollbackProcessTx();
            }

        val dbo = implFor(dboId).as(DBProcessorImpl<Message>);

        if (!abandoning)
            {
            try
                {
                using (val tx = ensureTransaction(dbo, override=True))
                    {
                    if (dbo.autoRetry(message, result, when, elapsed, timesAttempted))
                        {
                        dbo.retrying_(pid, elapsed, result);
                        }
                    else
                        {
                        abandoning = True;
                        }
                    }
                }
            catch (Exception e)
                {
                log($|While attempting to determine whether a message ({safeToString(message)})\
                     | should be retried for {dbo.dbPath.toString().substring(1)}, an exception\
                     | occurred: {e}
                   );
                abandoning = True;
                }
            }

        if (abandoning)
            {
            try
                {
                using (val tx = ensureTransaction(dbo, override=True))
                    {
                    dbo.abandoning_(pid, elapsed, result);
                    dbo.abandon(message, result, when, elapsed, timesAttempted);
                    }
                }
            catch (Exception e)
                {
                log($|While notifying {dbo.dbPath.toString().substring(1)} of an abandoned message\
                     | ({safeToString(message)}), an exception occurred: {e}
                   );
                }
            }

        return abandoning;
        }

    /**
     * Verify that the previous requirement still holds true.
     *
     * @param req  the requirement
     *
     * @return True if the same result is produced as indicated in the requirement
     */
    <Result extends immutable Const> Result evaluateRequirement(Int dboId, function Result(DBObjectImpl) test)
        {
        assert internal;

        DBObjectImpl dbo = implFor(dboId);
        return test(dbo);
        }

    /**
     * Perform all configured validation checks on the specified DBObject.
     *
     * @param dboId  the id of the DBObject to process the validations for
     *
     * @return True if the validation succeeded
     */
    Boolean validateDBObject(Int dboId)
        {
        assert internal;

        ObjectStore store = storeFor(dboId);
        store.triggerBegin(tx?.id_ : assert);
        try
            {
            return implFor(dboId).validate_();
            }
        finally
            {
            store.triggerEnd(tx?.id_ : assert);
            }
        }

    /**
     * Perform all configured data rectification steps on the specified DBObject.
     *
     * @param dboId  the id of the DBObject to process the rectifications for
     *
     * @return True if the rectification succeeded
     */
    Boolean rectifyDBObject(Int dboId)
        {
        assert internal;

        ObjectStore store = storeFor(dboId);
        store.triggerBegin(tx?.id_ : assert);
        try
            {
            return implFor(dboId).rectify_();
            }
        finally
            {
            store.triggerEnd(tx?.id_ : assert);
            }
        }

    /**
     * Perform all configured data distributions steps on the specified DBObject.
     *
     * @param dboId  the id of the DBObject to process the data distribution for
     *
     * @return True if the data distribution succeeded
     */
    Boolean distributeDBObject(Int dboId)
        {
        assert internal;

        ObjectStore store = storeFor(dboId);
        store.triggerBegin(tx?.id_ : assert);
        try
            {
            return implFor(dboId).distribute_();
            }
        finally
            {
            store.triggerEnd(tx?.id_ : assert);
            }
        }

    /**
     * For the transaction manager's internal clients that emulate various stages in a transaction,
     * this terminates the representation of a previously-specified transaction, allowing the client
     * to be safely returned to a pool for later re-use.
     */
    void stopRepresentingTransaction()
        {
        assert internal;
        if (Transaction tx ?= this.tx)
            {
            this.tx = Null;
            tx.id_  = NO_TX;
            }

        if (val client ?= preTxView)
            {
            client.stopRepresentingTransaction();
            txManager.recycleClient(client);
            preTxView = Null;
            }
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    DBObjectInfo infoFor(Int id)
        {
        return catalog.infoFor(id);
        }

    /**
     * Obtain the DBObjectImpl for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectImpl for the specified id
     */
    DBObjectImpl implFor(Int id)
        {
        DBObjectImpl?[] impls = appObjects;
        Int             index = id;
        if (id < 0)
            {
            impls = sysObjects;
            index = BuiltIn.byId(id).ordinal;
            }

        Int size = impls.size;
        if (index < size)
            {
            return impls[index]?;
            }

        DBObjectImpl impl = createImpl(id);

        // save off the ObjectStore (lazy cache)
        if (index > impls.size)
            {
            impls.fill(Null, impls.size..index);
            }
        impls[index] = impl;

        return impl;
        }

    /**
     * Create an DBObjectImpl for the specified internal database object id.
     *
     * @param id  the internal object id
     *
     * @return the new DBObjectImpl
     */
    DBObjectImpl createImpl(Int id)
        {
        if (id <= 0)
            {
            DBObjectInfo info  = infoFor(id);
            return switch (BuiltIn.byId(info.id))
                {
                case Root:         new RootSchemaImpl(info);
                case Sys:          new SystemSchemaImpl(info);
                case Info:         TODO new DBValue<DBInfo>();              // TODO ...
                case Users:        TODO new DBMap<String, DBUser>();
                case Types:        TODO new DBMap<String, Type>();
                case Objects:      TODO new DBMap<String, DBObject>();
                case Schemas:      TODO new DBMap<String, DBSchema>();
                case Counters:     TODO new DBMap<String, DBCounter>();
                case Values:       TODO new DBMap<String, DBValue>();
                case Maps:         TODO new DBMap<String, DBMap>();
                case Lists:        TODO new DBMap<String, DBList>();
                case Queues:       TODO new DBMap<String, DBQueue>();
                case Processors:   TODO new DBMap<String, DBProcessor>();
                case Logs:         TODO new DBMap<String, DBLog>();
                case Pending:      TODO new DBList<Pending>();
                case Transactions: TODO new DBLog<DBTransaction>();
                case Errors:       TODO new DBLog<String>();
                default: assert;
                };
            }

        DBObjectInfo info = infoFor(id);
        return switch (info.category)
            {
            case DBSchema:    new DBSchemaImpl(info);
            case DBCounter:   new DBCounterImpl(info, storeFor(id).as(CounterStore));
            case DBValue:     createDBValueImpl(info, storeFor(id).as(ValueStore));
            case DBMap:       createDBMapImpl(info, storeFor(id).as(MapStore));
            case DBList:      TODO
            case DBQueue:     TODO
            case DBProcessor: createDBProcessorImpl(info, storeFor(id).as(ProcessorStore));
            case DBLog:       createDBLogImpl  (info, storeFor(id).as(LogStore));
            };
        }

    private DBMapImpl createDBMapImpl(DBObjectInfo info, MapStore store)
        {
        assert Type keyType := info.typeParams.get("Key"),
                    keyType.is(Type<immutable Const>);
        assert Type valType := info.typeParams.get("Value"),
                    valType.is(Type<immutable Const>);

        return new DBMapImpl<keyType.DataType, valType.DataType>(info, store);
        }

    private DBLogImpl createDBLogImpl(DBObjectInfo info, LogStore store)
        {
        assert Type elementType := info.typeParams.get("Element"),
                    elementType.is(Type<immutable Const>);

        return new DBLogImpl<elementType.DataType>(info, store);
        }

    private DBProcessorImpl createDBProcessorImpl(DBObjectInfo info, ProcessorStore store)
        {
        assert Type messageType := info.typeParams.get("Message"),
                    messageType.is(Type<immutable Const>);

        return new DBProcessorImpl<messageType.DataType>(info, store);
        }

    private DBValueImpl createDBValueImpl(DBObjectInfo info, ValueStore store)
        {
        assert Type valueType := info.typeParams.get("Value"),
                    valueType.is(Type<immutable Const>);

        return new DBValueImpl<valueType.DataType>(info, store);
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    ObjectStore storeFor(Int id)
        {
        return catalog.storeFor(id);
        }


    // ----- Worker --------------------------------------------------------------------------------

    /**
     * The Worker service exists to allow work to be delegated explicitly back to the Container that
     * the Client is running within.
     *
     * This allows CPU-intensive (expensive) work to be dumped back onto the Client instead of
     * letting that work fall onto more critical services, such AS the various `ObjectStore`
     * services.
     */
    static service Worker(json.Schema jsonSchema)
        {
        /**
         * Deserialize a value from a JSON string.
         *
         * @param mapping   the JSON mapping to use for deserialization
         * @param jsonText  the String containing the JSON formatted value
         *
         * @return the deserialized value
         */
        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, String jsonText)
            {
            return mapping.read(new ObjectInputStream(jsonSchema, jsonText.toReader()).ensureElementInput());
            }

        /**
         * Deserialize a value from JSON tokens.
         *
         * @param mapping     the JSON mapping to use for deserialization
         * @param jsonTokens  the previously lexed JSON tokens
         *
         * @return the deserialized value
         */
        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, immutable Token[] jsonTokens)
            {
            return mapping.read(new ObjectInputStream(jsonSchema, jsonTokens.iterator()).ensureElementInput());
            }

        /**
         * Deserialize a value from a JSON string.
         *
         * @param mapping  the JSON mapping to use for serialization
         * @param value    the value to serialize
         *
         * @return a String containing the JSON formatted value
         */
        <Serializable> String writeUsing(Mapping<Serializable> mapping, immutable Serializable value)
            {
            val buf    = new StringBuffer();
            val stream = new ObjectOutputStream(jsonSchema, buf);
            mapping.write(stream.createElementOutput(), value);
            stream.close();
            return buf.toString();
            }
        }


    // ----- TxContext -----------------------------------------------------------------------------

    /**
     * The TxContext simplifies "autocommit" transaction management on an operation-by-operation basis.
     */
    protected class TxContext
            implements Closeable
        {
        private Boolean autocommit;
        private Int     depth;

        void enter(Boolean autocommit)
            {
            if (depth++ == 0)
                {
                this.autocommit = autocommit;
                }
            }

        Int id.get()
            {
            return outer.tx?.id_ : assert;
            }

        @Override void close(Exception? e = Null)
            {
            assert depth > 0;
            assert Transaction tx ?= outer.tx;

            if (--depth == 0 && autocommit)
                {
                if (e == Null)
                    {
                    val result = tx.commit();
                    if (result != Committed)
                        {
                        throw new CommitFailed(tx.txInfo, result,
                            $"Failed to auto-commit {tx}; reason={result}");
                        }
                    }
                else
                    {
                    tx.rollback();
                    }
                }
            }
        }

    /**
     * The non-transactional TxContext.
     */
    protected class NtxContext
            extends TxContext
        {
        @Override
        void enter(Boolean autocommit)
            {
            }

        @Override
        Int id.get()
            {
            return NO_TX;
            }

        @Override
        void close(Exception? e = Null)
            {
            }
        }


    // ----- DBObject ------------------------------------------------------------------------------

    /**
     * The shared base implementation for all of the client DBObject representations.
     */
    @Abstract class DBObjectImpl(DBObjectInfo info_)
            implements DBObject
        {
        protected DBObjectInfo info_;

        /**
         * Holds a deferred function in a linked list of deferred functions for this DBObjectImpl.
         */
        class Deferred_(
                DBObjectImpl                    dbo,
                function Boolean(DBObjectImpl)? adjust,
                )
            {
            /**
             * The next deferred for the same transaction.
             */
            Deferred_? txNextDeferred = Null;

            /**
             * The next Deferred for the same DBObject.
             */
            Deferred_? dboNextDeferred = Null;
            }

        /**
         * The first deferred adjustment function for this DBObject in the current transaction.
         */
        protected Deferred_? dboFirstDeferred_ = Null;

        /**
         * The last deferred adjustment function for this DBObject in the current transaction.
         */
        protected Deferred_? dboLastDeferred_ = Null;

        @Override
        @RO (Connection + Schema) dbConnection.get()
            {
            return outer.conn ?: assert;
            }

        @Override
        @RO (Transaction + Schema)? dbTransaction.get()
            {
            return outer.tx;
            }

        @Override
        @RO DBObject!? dbParent.get()
            {
            return implFor(info_.parentId);
            }

        @Override
        @RO DBCategory dbCategory.get()
            {
            return info_.category;
            }

        @Override
        @RO String dbName.get()
            {
            return info_.name;
            }

        @Override
        @RO Path dbPath.get()
            {
            return info_.path;
            }

        @Override
        @Lazy Map<String, DBObject> dbChildren.calc()
            {
            return new Map()
                {
                @Override
                conditional DBObject get(String key)
                    {
                    if (DBObjectInfo info := infos.get(key))
                        {
                        return True, implFor(info.id);
                        }

                    return False;
                    }

                @Override
                @Lazy Set<String> keys.calc()
                    {
                    return infos.keys;
                    }

                protected @Lazy Map<String, DBObjectInfo> infos.calc()
                    {
                    Int[] childIds = info_.childIds;
                    Int   size     = childIds.size;
                    if (size == 0)
                        {
                        return Map:[];
                        }

                    ListMap<String, DBObjectInfo> infos = new ListMap(size);
                    childIds.associate(i -> {val info = infoFor(i); return info.name, info;}, infos);
                    return infos.freeze();
                    }
                };
            }

        @Override
        <Result extends immutable Const> Result require(function Result(DBObject) test)
            {
            Transaction tx = requireTransaction_("require()");
            return outer.txManager.registerRequirement(tx.id_, info_.id, test);
            }

        @Override
        void defer(function Boolean(DBObjectImpl) adjust)
            {
            Transaction tx = requireTransaction_("defer()");
            Deferred_ deferred = new Deferred_(this, adjust);
            tx.txAddDeferred_(deferred);
            dboAddDeferred_(deferred);
            }

        /**
         * Add a deferred adjustment to this DBObjectImpl's list of deferred adjustments.
         */
        protected void dboAddDeferred_(Deferred_ deferred)
            {
            if (Deferred_ dboLastDeferred ?= dboLastDeferred_)
                {
                dboLastDeferred.dboNextDeferred = deferred;
                dboLastDeferred_ = deferred;
                }
            else
                {
                dboFirstDeferred_ = deferred;
                dboLastDeferred_  = deferred;
                }
            }

        /**
         * Process all of this DBObjectImpl's list of deferred adjustments.
         *
         * @return True iff no deferred adjustment reported a failure
         */
        protected Boolean dboProcessDeferred_()
            {
            while (Deferred_ deferred ?= dboFirstDeferred_)
                {
                dboFirstDeferred_ = deferred.dboNextDeferred;

                if (function Boolean(DBObjectImpl) adjust ?= deferred.adjust)
                    {
                    // wipe out the deferred work (so it doesn't accidentally get re-run in the
                    // future)
                    deferred.adjust = Null;

                    Boolean failure = False;
                    try
                        {
                        if (!adjust(this))
                            {
                            failure = True;
                            }
                        }
                    catch (Exception e)
                        {
                        failure = True;

                        // log the exception (otherwise the information would be lost)
                        log($|While attempting to execute a deferred adjustment on\
                             | {dbPath.toString().substring(1)}, an exception occurred: {e}
                           );
                        }

                    if (failure)
                        {
                        // mark the transaction as rollback-only
                        assert Transaction tx ?= outer.tx;
                        tx.rollbackOnly = True;

                        // deferred adjustments failed
                        return False;
                        }
                    }
                }

            return True;
            }

        /**
         * Reset the linked list of Deferred items on this DBObjectImpl.
         */
        protected void dboResetDeferred_()
            {
            dboFirstDeferred_ = Null;
            dboLastDeferred_  = Null;
            }

        /**
         * Require both that this DBObject be transactional, and that a transaction already exist.
         *
         * @param method  the method name or description to display in the assertion message
         */
        Transaction requireTransaction_(String method)
            {
            // this DBObject must be transactional
            assert this.transactional as $|DBObject {dbPath.toString().substring(1)} is not a\
                                          | transactional object; {method} requires a transaction
                                         ;

            // there must already be a transaction
            if (Transaction tx ?= outer.tx)
                {
                return tx;
                }
            assert as "No transaction exists; {method} requires a transaction";
            }

        /**
         * Perform all configured validation checks on this DBObject.
         *
         * @return True if the validation succeeded
         */
        Boolean validate_()
            {
            TxChange change = new TxChange_(); // TODO CP cache?

            for (val validator : info_.validators)
                {
                try
                    {
                    if (!validator.validate(change))
                        {
                        return False;
                        }
                    }
                catch (Exception e)
                    {
                    this.Client.log($"An exception occurred while evaluating Validator \"{&validator.actualClass.displayName}\": {e}");
                    return False;
                    }
                }

            return True;
            }

        /**
         * Perform all configured rectification on this DBObject.
         *
         * @return True if the rectifiers succeeded
         */
        Boolean rectify_()
            {
            TxChange change = new TxChange_();

            for (val rectifier : info_.rectifiers)
                {
                try
                    {
                    if (!rectifier.rectify(change))
                        {
                        return False;
                        }
                    }
                catch (Exception e)
                    {
                    this.Client.log($"An exception occurred while processing Rectifier \"{&rectifier.actualClass.displayName}\": {e}");
                    return False;
                    }
                }

            return True;
            }

        /**
         * Perform all configured distribution operations for this DBObject.
         *
         * @return True if the distribution succeeded
         */
        Boolean distribute_()
            {
            TxChange change = new TxChange_();

            for (val distributor : info_.distributors)
                {
                try
                    {
                    if (!distributor.process(change))
                        {
                        return False;
                        }
                    }
                catch (Exception e)
                    {
                    this.Client.log($"An exception occurred while processing Distributor \"{&distributor.actualClass.displayName}\": {e}");
                    return False;
                    }
                }

            return True;
            }

        /**
         * An implementation of the "before tx" and "after tx" views of the transactional changes
         * to the enclosing DBObject.
         */
        class TxChange_
                implements TxChange
            {
            @Override
            @Lazy DBObject pre.calc()
                {
                return ensurePreTxClient().implFor(info_.id);
                }

            @Override
            DBObject post.get()
                {
                return outer;
                }
            }
        }


    // ----- DBSchema ------------------------------------------------------------------------------

    /**
     * The DBSchema DBObject implementation.
     */
    class DBSchemaImpl(DBObjectInfo info_)
            extends DBObjectImpl(info_)
            implements DBSchema
        {
        @Override
        @RO DBSchema!? dbParent.get()
            {
            return info_.id == 0
                    ? Null
                    : super().as(DBSchema);
            }
        }


    // ----- RootSchema ----------------------------------------------------------------------------

    /**
     * The RootSchema DBObject implementation.
     */
    class RootSchemaImpl(DBObjectInfo info_)
            extends DBSchemaImpl(info_)
            implements RootSchema
        {
        @Override
        SystemSchema sys.get()
            {
            return this.Client.implFor(BuiltIn.Sys.id).as(SystemSchema);
            }
        }


    // ----- SystemSchema --------------------------------------------------------------------------

    /**
     * The SystemSchema DBObject implementation.
     */
    class SystemSchemaImpl(DBObjectInfo info_)
            extends DBSchemaImpl(info_)
            implements SystemSchema
        {
        @Override
        @RO DBValue<DBInfo> info.get()
            {
            return implFor(BuiltIn.Info.id).as(DBValue<DBInfo>);
            }

        @Override
        @RO DBMap<String, DBUser> users.get()
            {
            return implFor(BuiltIn.Users.id).as(DBMap<String, DBUser>);
            }

        @Override
        @RO DBMap<String, Type> types.get()
            {
            return implFor(BuiltIn.Types.id).as(DBMap<String, Type>);
            }

        @Override
        @RO DBMap<String, DBObject> objects.get()
            {
            return implFor(BuiltIn.Objects.id).as(DBMap<String, DBObject>);
            }

        @Override
        @RO DBMap<String, DBSchema> schemas.get()
            {
            return implFor(BuiltIn.Schemas.id).as(DBMap<String, DBSchema>);
            }

        @Override
        @RO DBMap<String, DBCounter> counters.get()
            {
            return implFor(BuiltIn.Counters.id).as(DBMap<String, DBCounter>);
            }

        @Override
        @RO DBMap<String, DBValue> values.get()
            {
            return implFor(BuiltIn.Values.id).as(DBMap<String, DBValue>);
            }

        @Override
        @RO DBMap<String, DBMap> maps.get()
            {
            return implFor(BuiltIn.Maps.id).as(DBMap<String, DBMap>);
            }

        @Override
        @RO DBMap<String, DBList> lists.get()
            {
            return implFor(BuiltIn.Lists.id).as(DBMap<String, DBList>);
            }

        @Override
        @RO DBMap<String, DBQueue> queues.get()
            {
            return implFor(BuiltIn.Queues.id).as(DBMap<String, DBQueue>);
            }

        @Override
        @RO DBMap<String, DBProcessor> processors.get()
            {
            return implFor(BuiltIn.Processors.id).as(DBMap<String, DBProcessor>);
            }

        @Override
        @RO DBMap<String, DBLog> logs.get()
            {
            return implFor(BuiltIn.Logs.id).as(DBMap<String, DBLog>);
            }

        @Override
        @RO DBList<Pending> pending.get()
            {
            return implFor(BuiltIn.Pending.id).as(DBList<Pending>);
            }

        @Override
        @RO DBLog<DBTransaction> transactions.get()
            {
            return implFor(BuiltIn.Transactions.id).as(DBLog<DBTransaction>);
            }

        @Override
        @RO DBLog<String> errors.get()
            {
            return implFor(BuiltIn.Errors.id).as(DBLog<String>);
            }
        }

    // TODO cut & paste from deleted source files - /sys/ stuff to sort out later
    //    @Override
    //    DBInfo get()
    //        {
    //        // TODO can cache a lot of this information on the Catalog
    //        return new DBInfo(
    //                name     = catalog.dir.toString(),
    //                version  = catalog.version ?: assert,   // TODO review the assert
    //                created  = catalog.statusFile.created,
    //                modified = catalog.statusFile.modified, // TODO get timestamp from last tx
    //                accessed = catalog.dir.accessed.maxOf(catalog.statusFile.modified),
    //                readable = catalog.statusFile.readable,
    //                writable = catalog.statusFile.writable && !catalog.readOnly,
    //                size     = catalog.dir.size);
    //        }


    // ----- Connection ----------------------------------------------------------------------------

    /**
     * The Connection API, for providing to a database client.
     */
    class Connection(DBObjectInfo info_)
            extends RootSchemaImpl(info_)
            implements oodb.Connection<Schema>
        {
        @Override
        @RO DBUser dbUser.get()
            {
            return outer.dbUser;
            }

        @Override
        @RO (Transaction + Schema)? transaction.get()
            {
            return outer.tx? : Null;
            }

        @Override
        (Transaction + Schema) createTransaction(UInt?                  id          = Null,
                                                 String?                name        = Null,
                                                 DBTransaction.Priority priority    = Normal,
                                                 Boolean                readOnly    = False,
                                                 Duration?              timeout     = Null,
                                                 Int                    retryCount  = 0,
                                                 Boolean                override    = False,
                                                )
            {
            assert outer.tx == Null as "Attempted to create a transaction when one already exists";
            assert !internal && !override;

            id ?:= outer.txManager.generateTxId();
            TxInfo txInfo = new TxInfo(id, name, priority, readOnly, timeout, retryCount);

            (Transaction + Schema) newTx = new Transaction(info_, txInfo).as(Transaction + Schema);

            outer.tx = newTx;
            return newTx;
            }

        @Override
        void close(Exception? e = Null)
            {
            super(e);
            outer.conn = Null;
            outer.tx   = Null;
            notifyOnClose?(this.Client);
            }
        }


    // ----- Transaction ---------------------------------------------------------------------------

    /**
     * The Transaction API, for providing to a database client.
     */
    class Transaction(DBObjectInfo info_, TxInfo txInfo)
            extends RootSchemaImpl(info_)
            implements oodb.Transaction<Schema>
        {
        construct(DBObjectInfo info_, TxInfo txInfo, Int? id=Null)
            {
            construct RootSchemaImpl(info_);
            this.txInfo = txInfo;
            }
        finally
            {
            id_ = id ?: txManager.begin(this, worker, readOnly || txInfo.readOnly);
            }

        /**
         * The set of DBObject ids with deferred transactional processing.
         */
        protected DBObjectImpl.Deferred_? txFirstDeferred_ = Null;

        /**
         * The set of DBObject ids with deferred transactional processing.
         */
        protected DBObjectImpl.Deferred_? txLastDeferred_ = Null;

        /**
         * Alter the prepare-stage transaction to represent a new stage of the prepare process.
         *
         * @param txInfo
         * @param id
         */
        void represent_(TxInfo txInfo, Int id)
            {
            this.txInfo = txInfo;
            this.id_    = id;
            }

        /**
         * The transaction ID assigned to this transaction by the TxManager.
         *
         * Internally, this ID is known as the transaction's "write id", but here on the client,
         * it's just "the id". (Note that this id has no relation to the application-specified
         * transaction id that is held in the TxInfo, and has no meaning within the database.)
         */
        public/protected Int id_ = NO_TX;

        @Override
        public/protected TxInfo txInfo;

        @Override
        @RO (Connection + Schema) connection.get()
            {
            // note: this is considered to be a valid request, regardless of whether this
            // transaction is a currently valid transaction or not
            return outer.conn ?: assert;
            }

        @Override
        @RO Boolean pending.get()
            {
            return &this == outer.&tx;
            }

        @Override
        Boolean rollbackOnly.set(Boolean value)
            {
            assert pending;
            assert value || !get();
            super(value);
            }

        @Override
        CommitResult commit(Boolean override = False)
            {
            Transaction? that = outer.tx;
            if (that == Null)
                {
                log($"Attempt to commit a previously closed transaction {this}; no current transaction.");
                return PreviouslyClosed;
                }

            if (&this != &that)
                {
                log($"Attempt to commit a previously closed transaction {this}; a different transaction is now in progress.");
                return PreviouslyClosed;
                }

            if (outer.internal && !override)
                {
                log($"Illegal commit request for {this}.");
                // technically, this error is not correct, but the gist is correct: this transaction
                // is not allowed to be committed
                return RollbackOnly;
                }

            // a transaction with a NO_TX id or a ReadId hasn't done anything to commit
            CommitResult result = Committed;
            if (id_ != NO_TX && TxManager.txCat(id_) == Open)
                {
                if (!txProcessDeferred_())
                    {
                    return DeferredFailed;
                    }

                try
                    {
                    result = txManager.commit(id_);
                    }
                catch (Exception e)
                    {
                    log($"Exception during commit of {this}: {e}");
                    result = DatabaseError;
                    try
                        {
                        txManager.rollback(id_);
                        }
                    catch (Exception ignore) {}
                    }
                finally
                    {
                    // clearing out the transaction reference will "close" the transaction; it
                    // becomes no longer reachable internally
                    outer.tx = Null;
                    }
                }

            close();

            return result;
            }

        @Override
        Boolean rollback(Boolean override = False)
            {
            Transaction? that = outer.tx;
            if (that == Null)
                {
                log($"Attempt to roll back a previously closed transaction {this}; no current transaction.");
                return False;
                }

            if (&this != &that)
                {
                log($"Attempt to roll back a previously closed transaction {this}; a different transaction is now in progress.");
                return False;
                }

            if (outer.internal && !override)
                {
                log($"Illegal rollback request for {this}.");
                return False;
                }

            Boolean result = True;
            if (id_ != NO_TX)
                {
                try
                    {
                    result = txManager.rollback(id_);
                    }
                catch (Exception e)
                    {
                    log($"Exception during rollback of {this}: {e}");
                    result = False;
                    }
                finally
                    {
                    outer.tx = Null;
                    }
                }

            close();

            return result;
            }

        @Override
        void close(Exception? e = Null)
            {
            Transaction? that = outer.tx;
            if (&this == &that)
                {
                txResetDeferred_();
                try
                    {
                    // this needs to eventually make its way to the implementation of close() on the
                    // Transaction interface itself, which will decide to either commit or to roll
                    // back the transaction, in the case that the transaction is still open
                    super(e);
                    }
                finally
                    {
                    outer.tx = Null;
                    }
                }
            }

        /**
         * Add a deferred adjustment to this Transaction's list of deferred adjustments.
         */
        protected void txAddDeferred_(Deferred_ deferred)
            {
            if (Deferred_ txLastDeferred ?= txLastDeferred_)
                {
                txLastDeferred.txNextDeferred = deferred;
                txLastDeferred_ = deferred;
                }
            else
                {
                txFirstDeferred_ = deferred;
                txLastDeferred_  = deferred;
                }
            }

        /**
         * Process all of this DBObjectImpl's list of deferred adjustments.
         *
         * @return True iff no deferred adjustment reported a failure
         */
        protected Boolean txProcessDeferred_()
            {
            // fast path - nothing is deferred
            if (txFirstDeferred_ == Null)
                {
                return True;
                }

            Boolean       failure = False;
            DBObjectImpl? prevDbo = Null;
            while (Deferred_ deferred ?= txFirstDeferred_)
                {
                txFirstDeferred_ = deferred.txNextDeferred;

                DBObjectImpl dbo = deferred.dbo;
                if (dbo != prevDbo)
                    {
                    dbo.dboResetDeferred_();
                    }

                if (!failure, function Boolean(DBObjectImpl) adjust ?= deferred.adjust)
                    {
                    // wipe out the deferred work (so it doesn't accidentally get re-run in the
                    // future)
                    deferred.adjust = Null;

                    try
                        {
                        if (!adjust(dbo))
                            {
                            failure = True;
                            }
                        }
                    catch (Exception e)
                        {
                        failure = True;

                        // log the exception (otherwise the information would be lost)
                        log($|While attempting to execute a deferred adjustment on\
                             | {dbo.dbPath.toString().substring(1)}, an exception occurred: {e}
                           );
                        }
                    }
                }

            txResetDeferred_();

            if (failure)
                {
                rollbackOnly = True;

                // deferred adjustments failed
                return False;
                }

            return True;
            }

        /**
         * Reset the linked list of Deferred items on this DBObjectImpl.
         */
        protected void txResetDeferred_()
            {
            if (DBObjectImpl.Deferred_ deferred ?= txFirstDeferred_)
                {
                DBObjectImpl? prevDbo = Null;
                do
                    {
                    // minor optimization: if there are a bunch of deferred adjustments in a row for
                    // the same DBObject, then only call reset once
                    DBObjectImpl dbo = deferred.dbo;
                    if (dbo != prevDbo)
                        {
                        dbo.dboResetDeferred_();
                        prevDbo = dbo;
                        }
                    }
                while (deferred ?= deferred.txNextDeferred);

                txFirstDeferred_ = Null;
                txLastDeferred_  = Null;
                }
            }

        @Override
        String toString()
            {
            return $"Transaction(id={id_}, txInfo={txInfo})";
            }
        }


    // ----- DBValue -------------------------------------------------------------------------------

    /**
     * The DBValue implementation.
     */
    class DBValueImpl<Value extends immutable Const>(DBObjectInfo info_, ValueStore<Value> store_)
            extends DBObjectImpl(info_)
            implements DBValue<Value>
        {
        protected ValueStore<Value> store_;

        @Override
        Value get()
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                return store_.load(tx.id);
                }
            }

        @Override
        void set(Value value)
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                store_.store(tx.id, value);
                }
            }
        }


    // ----- DBCounter -----------------------------------------------------------------------------

    /**
     * The DBCounter DBObject implementation.
     */
    class DBCounterImpl(DBObjectInfo info_, CounterStore store_)
            extends DBValueImpl<Int>(info_, store_)
            implements DBCounter
        {
        @Override
        Boolean transactional.get()
            {
            return info_.transactional;
            }

        @Override
        Int next(Int count = 1)
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                return store_.adjust(tx.id, count);
                }
            }

        @Override
        void adjustBy(Int value)
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                store_.adjustBlind(tx.id, value);
                }
            }

        @Override
        Int preIncrement()
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                (_, Int after) = store_.adjust(tx.id, 1);
                return after;
                }
            }

        @Override
        Int preDecrement()
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                (_, Int after) = store_.adjust(tx.id, -1);
                return after;
                }
            }

        @Override
        Int postIncrement()
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                return store_.adjust(tx.id, 1);
                }
            }

        @Override
        Int postDecrement()
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                return store_.adjust(tx.id, -1);
                }
            }
        }


    // ----- DBMap ---------------------------------------------------------------------------------

    /**
     * The DBMap implementation.
     */
    class DBMapImpl<Key extends immutable Const, Value extends immutable Const>
            (DBObjectInfo info_, MapStore<Key, Value> store_)
            extends DBObjectImpl(info_)
            implements DBMap<Key, Value>
            incorporates KeySetBasedMap<Key, Value>
        {
        protected MapStore<Key, Value> store_;

        @Override
        @RO Int size.get()
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.sizeAt(tx.id);
                }
            }

        @Override
        @RO Boolean empty.get()
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.emptyAt(tx.id);
                }
            }

        @Override
        Boolean contains(Key key)
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.existsAt(tx.id, key);
                }
            }

        @Override
        conditional Value get(Key key)
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.load(tx.id, key);
                }
            }

        @Override
        DBMapImpl put(Key key, Value value)
            {
            using (val tx = ensureTransaction(this))
                {
                store_.store(tx.id, key, value);
                return this;
                }
            }

        @Override
        DBMapImpl remove(Key key)
            {
            using (val tx = ensureTransaction(this))
                {
                store_.delete(tx.id, key);
                return this;
                }
            }

        @Override
        @Lazy public/private Set<Key> keys.calc()
            {
            return new KeySet();
            }

        /**
         * A representation of the Keys from the MapStore.
         */
        protected class KeySet
                implements Set<Key>
            {
            @Override
            Int size.get()
                {
                return outer.size;
                }

            @Override
            Boolean empty.get()
                {
                return outer.empty;
                }

            @Override
            Iterator<Element> iterator()
                {
                return new KeyIterator();
                }

            /**
             * An iterator over the keys in the MapStore.
             */
            protected class KeyIterator
                    implements Iterator<Key>
                {
                /**
                 * Set to true once iteration has begun.
                 */
                protected/private Boolean started = False;

                /**
                 * The current block of keys.
                 */
                protected/private Key[] keyBlock = [];

                /**
                 * The index to use to get the next key.
                 */
                protected/private Int nextIndex = 0;

                /**
                 * The cookie to use to load the next block of keys.
                 */
                protected/private immutable Const? cookie = Null;

                /**
                 * Set to true once the iterator has been exhausted.
                 */
                protected/private Boolean finished.set(Boolean done)
                    {
                    if (done)
                        {
                        // make sure that the iterator has been marked as having started
                        started = True;
                        }

                    super(done);
                    }

                @Override
                conditional Element next()
                    {
                    while (True)
                        {
                        if (nextIndex < keyBlock.size)
                            {
                            return True, keyBlock[nextIndex++];
                            }

                        if (started && cookie == Null || finished)
                            {
                            close();
                            return False;
                            }

                        // if there is no transaction, then we'll be creating an auto-commit
                        // transaction, but if we already have a cookie left over from a
                        // previous partial iteration, we can't guarantee that we will get the
                        // same read tx-id from the new autocommit transaction
                        assert !started || this.Client.tx != Null as
                                `|Unable to complete iteration in auto-commit mode;
                                 | the Map contained too many keys at the start of the iteration
                                 | to deliver them within a single autocommit transaction.
                                ;

                        // load the next (or the first) block of keys
                        started = True;
                        using (val tx = ensureTransaction(this.DBMapImpl))
                            {
                            (keyBlock, cookie) = store_.keysAt(tx.id, cookie);
                            nextIndex = 0;
                            }
                        }
                    }

                @Override
                Boolean knownDistinct()
                    {
                    return True;
                    }

                @Override
                conditional Int knownSize()
                    {
                    if (!started)
                        {
                        return True, outer.size;
                        }

                    if (finished)
                        {
                        return True, 0;
                        }

                    return False;
                    }

                @Override
                void close(Exception? cause = Null)
                    {
                    // idempotent clean-up, and clear all unnecessary references
                    finished  = True;
                    keyBlock  = [];
                    nextIndex = 0;
                    cookie    = Null;
                    }
                }

            @Override
            Boolean contains(Key key)
                {
                return outer.contains(key);
                }

            @Override
            KeySet remove(Key key)
                {
                outer.remove(key);
                return this;
                }
            }
        }


    // ----- DBLog ---------------------------------------------------------------------------------

    /**
     * The DBLog implementation.
     */
    class DBLogImpl<Value extends immutable Const>(DBObjectInfo info_, LogStore<Value> store_)
            extends DBObjectImpl(info_)
            implements DBLog<Value>
        {
        protected LogStore<Value> store_;

        @Override
        Boolean transactional.get()
            {
            return info_.transactional;
            }

        @Override
        DBLogImpl add(Value value)
            {
            using (val tx = ensureTransaction(this, allowNontransactional=True))
                {
                store_.append(tx.id, value);
                }
            return this;
            }

        @Override
        conditional List<
                         DBLog<Value>.        // TODO GG why is "DBLog<Value>." required for "Entry"
                         Entry> select((Range<DateTime>|Duration)? period = Null,
                                       DBUser?                     user   = Null,
                                       (UInt|Range<UInt>)?         txIds  = Null,
                                       String?                     txName = Null)
            {
            // TODO
            return False;
            }
        }


    // ----- DBProcessor ---------------------------------------------------------------------------

    /**
     * The DBProcessor implementation.
     */
    class DBProcessorImpl<Message extends immutable Const>(DBObjectInfo info_, ProcessorStore<Message> store_)
            extends DBObjectImpl(info_)
            implements DBProcessor<Message>
        {
        protected ProcessorStore<Message> store_;

        @Override
        void schedule(Message message, Schedule? when=Null)
            {
            using (val tx = ensureTransaction(this))
                {
                store_.schedule(tx.id, message, when);
                }
            }

        @Override
        void scheduleAll(Iterable<Message> messages, Schedule? when=Null)
            {
            using (val tx = ensureTransaction(this))
                {
                super(messages, when);
                }
            }

        @Override
        void reschedule(Message message, Schedule when)
            {
            using (val tx = ensureTransaction(this))
                {
                super(message, when);
                }
            }

        @Override
        void unschedule(Message message)
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.unschedule(tx.id, message);
                }
            }

        @Override
        void unscheduleAll()
            {
            using (val tx = ensureTransaction(this))
                {
                store_.unscheduleAll(tx.id);
                }
            }

        /**
         * Implements the list as returned from the pending() method.
         */
        class PendingList_(Int txid, Int[] pids)
                implements List<Pending>
            {
            /**
             * Cached Pending objects; the contents of this list.
             */
            Pending?[] pendingCache = new Pending?[];

            /**
             * Checks to make sure that this list isn't used outside of its transaction.
             */
            void checkTx()
                {
                assert txid == this.Client.tx?.id_ : False;
                }

            @Override
            Int size.get()
                {
                checkTx();
                return pids.size;
                }

            @Override
            @Op("[]") Pending getElement(Index index)
                {
                checkTx();
                assert:bounds 0 <= index < pids.size;
                if (index < pendingCache.size, Pending pending ?= pendingCache[index])
                    {
                    return pending;
                    }

                Pending pending = store_.pending(txid, pids[index]);

                // cache the result
                if (index >= pendingCache.size)
                    {
                    pendingCache.fill(Null, pendingCache.size..index);
                    }
                pendingCache[index] = pending;

                return pending;
                }

            @Override
            List<Pending> reify()
                {
                checkTx();
                return toArray();
                }
            }

        @Override
        List<Pending> pending()
            {
            using (val tx = ensureTransaction(this))
                {
                Int[] pids = store_.pidListAt(tx.id);
                List<Pending> list = new PendingList_(tx.id, pids);
                return tx.autocommit ? list.reify() : list;
                }
            }

        /**
         * Process one pending message.
         *
         * @param pid      indicates which Pending
         * @param message  the Message to process
         *
         * @return the elapsed processing time
         * @return the exceptional processing failure, iff the processing failed
         */
        (Range<DateTime>, Exception?) process_(Int pid, Message message)
            {
            Transaction     tx      = requireTransaction_("process()");
            Exception?      failure = Null;
            DateTime        start   = clock.now;
            Range<DateTime> elapsed;
            try
                {
                process(message);
                }
            catch (Exception e)
                {
                failure = e;
                }
            elapsed = start..clock.now;

            if (failure == Null)
                {
                // the information about the PID being successfully processed is part of the
                // transactional record, and must be committed as part of this transaction, so
                // the information is provided to the DBProcessor's ObjectStore at this point;
                // if the transaction fails to commit, then the fact that the PID was processed
                // will also be lost -- as it should be!
                store_.processCompleted(tx.id_, pid, elapsed);
                }

            return elapsed, failure;
            }

        @Override
        void process(Message message)
            {
            // this method must be overridden; the database schema is invalid if a DBProcessor
            // does not override this method
            }

        @Override
        void processAll(List<Message> messages)
            {
            using (val tx = ensureTransaction(this))
                {
                super(messages);
                }
            }

        @Override
        Boolean autoRetry(Message                  message,
                          CommitResult | Exception result,
                          Schedule?                when,
                          Range<DateTime>          elapsed,
                          Int                      timesAttempted)
            {
            using (val tx = ensureTransaction(this))
                {
                return super(message, result, when, elapsed, timesAttempted);
                }
            }

        /**
         * Notification of a decision to retry.
         */
        void retrying_(Int                      pid,
                       Range<DateTime>          elapsed,
                       CommitResult | Exception result)
            {
            Transaction tx = requireTransaction_("retryPending()");
            store_.retryPending(tx.id_, pid, elapsed, result);
            }

        /**
         * Notification of a decision to abandon.
         */
        void abandoning_(Int                      pid,
                         Range<DateTime>          elapsed,
                         CommitResult | Exception result)
            {
            Transaction tx = requireTransaction_("abandonPending()");
            store_.abandonPending(tx.id_, pid, elapsed, result);
            }

        @Override
        void abandon(Message                  message,
                     CommitResult | Exception result,
                     Schedule?                when,
                     Range<DateTime>          elapsed,
                     Int                      timesAttempted)
            {
            using (val tx = ensureTransaction(this))
                {
                super(message, result, when, elapsed, timesAttempted);
                }
            }

        @Override
        void suspend()
            {
            using (val tx = ensureTransaction(this))
                {
                store_.setEnabled(tx.id, False);
                }
            }

        @Override
        Boolean suspended.get()
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.isEnabled(tx.id);
                }
            }

        @Override
        void resume()
            {
            using (val tx = ensureTransaction(this))
                {
                store_.setEnabled(tx.id, True);
                }
            }
        }
    }
