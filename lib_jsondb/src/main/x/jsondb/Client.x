import collections.SparseIntSet;

import ecstasy.collections.CollectImmutableArray;

import ecstasy.maps.CursorEntry;
import ecstasy.maps.DiscreteEntry;
import ecstasy.maps.KeySetBasedMap;
import ecstasy.maps.MapEntries;
import ecstasy.maps.MapValues;

import json.Doc;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectOutputStream;

import oodb.DBClosed;
import oodb.DBCounter;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBObject.DBCategory;
import oodb.DBObjectInfo;
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

import model.DboInfo;

import storage.CounterStore;
import storage.LogStore;
import storage.MapStore;
import storage.ObjectStore;
import storage.ProcessorStore;
import storage.ValueStore;

import Catalog.BuiltIn;

import TxManager.NO_TX;

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
service Client<Schema extends RootSchema> {
    /**
     * Construct a Client service, representing a connection to the database and any current
     * transaction.
     *
     * @param catalog        the JSON db catalog, representing the database on disk
     * @param clientId       the id assigned to this `Client` service
     * @param dbUser         the database user to create the client on behalf of
     * @param readOnly       (optional) pass True to indicate that client is not permitted to modify
     *                       any data
     * @param notifyOnClose  the function to call when the client connection is closed
     */
    construct(Catalog<Schema>        catalog,
              Int                    clientId,
              DBUser                 dbUser,
              Boolean                readOnly = False,
              function void(Client)? notifyOnClose = Null) {
        assert Schema == RootSchema || catalog.metadata != Null;

        this.clientId      = clientId;
        this.dbUser        = dbUser;
        this.catalog       = catalog;
        this.clock         = catalog.clock;
        this.txManager     = catalog.txManager;
        this.jsonSchema    = catalog.jsonSchema;
        this.readOnly      = readOnly || catalog.readOnly;
        this.notifyOnClose = notifyOnClose;
    } finally {
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
     * The id assigned to this `Client` service.
     */
    public/private Int clientId;

    /**
     * The DBUser represented by this `Client` service.
     */
    public/private DBUser dbUser;

    /**
     * The [Connection] represented by this `Client` service. Set to `Null` when the `Connection` is
     * closed.
     */
    public/protected (Connection + Schema)? conn = Null;

    /**
     * The current [RootTransaction] on the Connection, or `Null` if no [Transaction] is active.
     */
    public/protected (RootTransaction + Schema)? rootTx = Null;

    /**
     * The current [Transaction] (either a [RootTransaction] or [NestedTransaction]) on the
     * [Connection], or `Null` if no [Transaction] is active.
     */
    @RO (Transaction + Schema)? currentTx.get() {
        (Transaction + Schema)? tx = rootTx;
        if (tx == Null) {
            return Null;
        }

        while (True) {
            val child = tx.child_;
            if (child == Null) {
                return tx;
            }
            tx = child.as(Transaction + Schema);
        }
    }

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
    @Concurrent
    protected void log(String msg) {
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
    static protected String safeToString(Object o) {
        try {
            return o.toString();
        } catch (Exception _) {
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
    @Concurrent
    Boolean checkRead() {
        if (conn == Null) {
            throw new DBClosed();
        }
        return True;
    }

    /**
     * Verify that the client connection is open and can be used for changing data.
     *
     * @return True if the check passes
     *
     * @throws Exception if the check fails
     */
    @Concurrent
    Boolean checkWrite() {
        assert !readOnly && !rootTx?.txInfo.readOnly;
        return checkRead();
    }

    /**
     * Obtain the current transaction, creating one if necessary, and wrapping it in a transactional
     * context.
     *
     * @param dbo              pass the [DBObject] instance that is requesting the transaction
     * @param allowNonTx       if no transaction already exists, and the DBObject is
     *                         non-transactional, then do not create a new transaction
     * @param allowOnInternal  allow this operation to occur on an "internal" connection
     *
     * @return the transactional context object
     */
    TxContext ensureTxContext(DBObjectImpl dbo, Boolean allowNonTx=False, Boolean allowOnInternal=False) {
        private TxContext ctx = new TxContext();
        private TxContext ntx = new NtxContext();

        checkRead();

        if (!dbo.transactional && allowNonTx) {
            return ntx;
        }

        Boolean autocommit = False;
        if (this.rootTx == Null) {
            assert !internal || allowOnInternal;
            if (Connection conn ?= this.conn) {
                conn.createTransaction(name="autocommit", allowOnInternal=allowOnInternal);
                autocommit = True;
            } else {
                throw new DBClosed();
            }
        }

        ctx.enter(autocommit);

        // if an error occurs processing deferred operations, there is no way to report the error
        // back from here; the tx will be marked rollback-only, but the client is in an entirely
        // different call, and not able to directly receive back any indication of the deferred
        // failure, so it can only determine this failure by checking the rollbackOnly property
        dbo.dboProcessDeferred_();

        return ctx;
    }

    /**
     * For the transaction manager's internal clients that emulate various stages in a transaction,
     * this sets the current transaction id for the client.
     *
     * @param txId  the transaction id to use as a read level
     */
    void representTransaction(Int txId) {
        assert internal;

        // update the readOnly property of the Client based on the requested transaction ID
        readOnly = switch (TxManager.txCat(txId)) {
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
        if (RootTransaction rootTx ?= this.rootTx) {
            rootTx.represent_(txInfo, txId);
        } else {
            // create a transaction to represent the requested transaction ID
            DboInfo dboInfo = infoFor(0); // the root schema
            rootTx = new RootTransaction(dboInfo, txInfo, txId).as(Transaction + Schema);
        }
    }

    /**
     * @return the internal Client that represents the version of the database before this
     *         transaction made changes
     */
    Client<Schema> ensurePreTxClient() {
        Client<Schema> client;
        if (client ?= preTxView) {
            return client;
        }

        assert internal;

        client = txManager.allocateClient();
        client.representTransaction(txManager.readIdFor(rootTx?.writeId_ : assert));
        preTxView = client;
        return client;
    }

    /**
     * True if this is an internal ("system") client.
     */
    @Concurrent
    @RO Boolean internal.get() = Catalog.isInternalClientId(clientId);

    /**
     * On behalf of the scheduler, create a new internal transaction.
     *
     * @param retryCount  the number of times this transaction has been retried (zero indicates the
     *                    first attempt)
     */
    void createProcessTx(Int retryCount) {
        assert internal;

        // roll back any previously left-over transaction (unlikely that this would ever occur)
        if (rootTx != Null) {
            rollbackProcessTx();
        }

        assert Connection conn ?= this.conn;
        conn.createTransaction(name="async", retryCount=retryCount, allowOnInternal=True);
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
    <Message extends immutable Const> (Range<Time>, Exception?) processMessage(Int dboId, Int pid, Message message) {
        assert internal;
        return implFor(dboId).as(DBProcessorImpl<Message>).process_(pid, message);
    }

    /**
     * On behalf of the scheduler, commit the current internal transaction.
     *
     * @return the CommitResult
     */
    CommitResult commitProcessTx() {
        assert internal;
        assert RootTransaction tx ?= this.rootTx;
        return tx.commit(allowOnInternal=True);
    }

    /**
     * On behalf of the scheduler, roll back the current internal transaction.
     */
    void rollbackProcessTx() {
        assert internal;
        if (RootTransaction tx ?= this.rootTx) {
            tx.rollback(allowOnInternal=True);
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
     * @param pendingId       indicates which Pending
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
            Int                      pendingId,
            Message                  message,
            CommitResult | Exception result,
            Schedule?                when,
            Range<Time>              elapsed,
            Int                      timesAttempted,
            Boolean                  abandoning,
            ) {
        assert internal;

        // roll back any previously left-over transaction (unlikely, but just in case)
        if (rootTx != Null) {
            rollbackProcessTx();
        }

        val dbo = implFor(dboId).as(DBProcessorImpl<Message>);

        if (!abandoning) {
            try {
                using (val txc = ensureTxContext(dbo, allowOnInternal=True)) {
                    if (dbo.autoRetry(message, result, when, elapsed, timesAttempted)) {
                        dbo.retrying_(message, pendingId, elapsed, result);
                    } else {
                        abandoning = True;
                    }
                }
            } catch (Exception e) {
                if (!e.is(DBClosed)) {
                    log($|While attempting to determine whether a message ({safeToString(message)})\
                         | should be retried for {dbo.dbPath.toString().substring(1)}, an exception\
                         | occurred: {e}
                       );
                }
                abandoning = True;
            }
        }

        if (abandoning) {
            try {
                using (val txc = ensureTxContext(dbo, allowOnInternal=True)) {
                    dbo.abandoning_(message, pendingId, elapsed, result);
                    dbo.abandon(message, result, when, elapsed, timesAttempted);
                }
            } catch (DBClosed e) {} catch (Exception e) {
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
    <Result extends immutable Const> Result evaluateRequirement(Int dboId, function Result(DBObjectImpl) test) {
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
    Boolean validateDBObject(Int dboId) {
        assert internal;

        ObjectStore store   = storeFor(dboId);
        Int         writeId = rootTx?.writeId_ : assert;
        store.triggerBegin(writeId);
        try {
            return implFor(dboId).validate_();
        } finally {
            store.triggerEnd(writeId);
        }
    }

    /**
     * Perform all configured data rectification steps on the specified DBObject.
     *
     * @param dboId  the id of the DBObject to process the rectifications for
     *
     * @return True if the rectification succeeded
     */
    Boolean rectifyDBObject(Int dboId) {
        assert internal;

        ObjectStore store   = storeFor(dboId);
        Int         writeId = rootTx?.writeId_ : assert;
        store.triggerBegin(writeId);
        try {
            return implFor(dboId).rectify_();
        } finally {
            store.triggerEnd(writeId);
        }
    }

    /**
     * Perform all configured data distributions steps on the specified DBObject.
     *
     * @param dboId  the id of the DBObject to process the data distribution for
     *
     * @return True if the data distribution succeeded
     */
    Boolean distributeDBObject(Int dboId) {
        assert internal;

        ObjectStore store   = storeFor(dboId);
        Int         writeId = rootTx?.writeId_ : assert;
        store.triggerBegin(writeId);
        try {
            return implFor(dboId).distribute_();
        } finally {
            store.triggerEnd(writeId);
        }
    }

    /**
     * For the transaction manager's internal clients that emulate various stages in a transaction,
     * this terminates the representation of a previously-specified transaction, allowing the client
     * to be safely returned to a pool for later re-use.
     */
    void stopRepresentingTransaction() {
        assert internal;
        if (Transaction tx ?= this.rootTx) {
            rootTx      = Null;
            tx.writeId_ = NO_TX;
        }

        if (val client ?= preTxView) {
            client.stopRepresentingTransaction();
            txManager.recycleClient(client);
            preTxView = Null;
        }
    }

    /**
     * Obtain the DboInfo for the specified id.
     *
     * @param dboId  the internal object id
     *
     * @return the DboInfo for the specified id
     */
    @Concurrent
    DboInfo infoFor(Int dboId) = catalog.infoFor(dboId);

    /**
     * Obtain the DBObjectImpl for the specified id.
     *
     * @param dboId  the internal object id
     *
     * @return the DBObjectImpl for the specified id
     */
    DBObjectImpl implFor(Int dboId) {
        DBObjectImpl?[] impls = appObjects;
        Int             index = dboId;
        if (dboId < 0) {
            impls = sysObjects;
            index = BuiltIn.byId(dboId).ordinal;
        }

        Int size = impls.size;
        if (index < size) {
            return impls[index]?;
        }

        DBObjectImpl impl = createImpl(dboId);

        // save off the ObjectStore (lazy cache)
        impls[index] = impl;

        return impl;
    }

    /**
     * Create an DBObjectImpl for the specified internal database object id.
     *
     * @param dboId  the internal object id
     *
     * @return the new DBObjectImpl
     */
    @Concurrent
    DBObjectImpl createImpl(Int dboId) {
        if (dboId <= 0) {
            DboInfo info  = infoFor(dboId);
            return switch (BuiltIn.byId(info.id)) {
                case Root:         new RootSchemaImpl(info);
                case Sys:          new SystemSchemaImpl(info);
                case Info:         TODO new DBValue<DBInfo>();              // TODO ...
                case Users:        TODO new DBMap<String, DBUser>();
                case Types:        TODO new DBMap<String, Type>();
                case Objects:      TODO new DBMap<String, DBObject>();
                case Schemas:      new SysMapImpl<DBObjectInfo>(info, DBSchema, catalog);
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

        DboInfo info = infoFor(dboId);
        return switch (info.category) {
            case DBSchema:    new DBSchemaImpl(info);
            case DBCounter:   new DBCounterImpl(info, storeFor(dboId).as(CounterStore));
            case DBValue:     createDBValueImpl(info, storeFor(dboId).as(ValueStore));
            case DBMap:       createDBMapImpl(info, storeFor(dboId).as(MapStore));
            case DBList:      TODO
            case DBQueue:     TODO
            case DBProcessor: createDBProcessorImpl(info, storeFor(dboId).as(ProcessorStore));
            case DBLog:       createDBLogImpl  (info, storeFor(dboId).as(LogStore));
        };
    }

    @Concurrent
    private DBMapImpl createDBMapImpl(DboInfo info, MapStore store) {
        Type keyType = info.typeParams[0].type;
        Type valType = info.typeParams[1].type;
        assert keyType.is(Type<immutable Const>);
        assert valType.is(Type<immutable Const>);

        return new DBMapImpl<keyType.DataType, valType.DataType>(info, store);
    }

    @Concurrent
    private DBLogImpl createDBLogImpl(DboInfo info, LogStore store) {
        Type elementType = info.typeParams[0].type;
        assert elementType.is(Type<immutable Const>);

        return new DBLogImpl<elementType.DataType>(info, store);
    }

    @Concurrent
    private DBProcessorImpl createDBProcessorImpl(DboInfo info, ProcessorStore store) {
        Type messageType = info.typeParams[0].type;
        assert messageType.is(Type<immutable Const>);

        return new DBProcessorImpl<messageType.DataType>(info, store);
    }

    @Concurrent
    private DBValueImpl createDBValueImpl(DboInfo info, ValueStore store) {
        Type valueType = info.typeParams[0].type;
        assert valueType.is(Type<immutable Const>);

        return new DBValueImpl<valueType.DataType>(info, store);
    }

    /**
     * Obtain the DboInfo for the specified id.
     *
     * @param dboId  the internal object id
     *
     * @return the DboInfo for the specified id
     */
    @Concurrent
    ObjectStore storeFor(Int dboId) = catalog.storeFor(dboId);

    // ----- Worker --------------------------------------------------------------------------------

    /**
     * The Worker service exists to allow work to be delegated explicitly back to the Container that
     * the Client is running within.
     *
     * This allows CPU-intensive (expensive) work to be dumped back onto the Client instead of
     * letting that work fall onto more critical services, such AS the various `ObjectStore`
     * services.
     */
    @Concurrent
    static service Worker(json.Schema jsonSchema) {
        /**
         * Deserialize a value from a JSON string.
         *
         * @param mapping   the JSON mapping to use for deserialization
         * @param jsonText  the String containing the JSON formatted value
         *
         * @return the deserialized value
         */
        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, String jsonText) {
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
        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, immutable Token[] jsonTokens) {
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
        <Serializable> String writeUsing(Mapping<Serializable> mapping, immutable Serializable value) {
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
            implements Closeable {

        private Boolean autocommit;
        private Int     depth;

        void enter(Boolean autocommit) {
            if (depth++ == 0) {
                this.autocommit = autocommit;
            }
        }

        Int writeId.get() = outer.rootTx?.writeId_ : assert;

        @Override void close(Exception? e = Null) {
            assert depth > 0;
            if (--depth == 0 && autocommit) {
                if  (Transaction tx ?= outer.currentTx) {
                    if (e == Null) {
                        val result = tx.commit();
                        if (result != Committed) {
                            throw new CommitFailed(tx.txInfo, result,
                                $"Failed to auto-commit {tx}; reason={result}");
                        }
                    } else {
                        tx.rollback();
                    }
                } else if (conn == Null || catalog.status != Running) {
                    throw new DBClosed();
                } else {
                    assert as "no current Transaction";
                }
            }
        }
    }

    /**
     * The non-transactional TxContext.
     */
    @Concurrent
    protected class NtxContext
            extends TxContext {
        @Override
        void enter(Boolean autocommit) {}

        @Override
        Int writeId.get() = NO_TX;

        @Override
        void close(Exception? e = Null) {}
    }

    // ----- DBObject ------------------------------------------------------------------------------

    /**
     * The shared base implementation for all of the client DBObject representations.
     */
    @Abstract class DBObjectImpl(DboInfo info_)
            implements DBObject {

        protected DboInfo info_;

        /**
         * Holds a deferred function in a linked list of deferred functions for this DBObjectImpl.
         */
        class Deferred_(
                DBObjectImpl                     dbo,
                function Boolean(DBObjectImpl!)? adjust,
                ) {
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
        @RO (Connection + Schema) connection.get() = outer.conn ?: throw new DBClosed();

        @Override
        @RO (Transaction + Schema)? transaction.get() = outer.currentTx;

        @Override
        @Concurrent
        @RO DBObject!? dbParent.get() = implFor(info_.parentId);

        @Override
        @Concurrent
        @RO DBCategory dbCategory.get() = info_.category;

        @Override
        @Concurrent
        @RO String dbName.get() = info_.name;

        @Override
        @Concurrent
        @RO Path dbPath.get() = info_.path;

        @Override
        @Concurrent
        @Lazy Map<String, DBObject> dbChildren.calc() = new Map() {
            @Override
            conditional DBObject get(String key) {
                if (DboInfo info := infos.get(key)) {
                    return True, implFor(info.id);
                }

                return False;
            }

            @Override
            Iterator<Entry<String, DBObject>> iterator() {
                return new Iterator() {
                    Iterator<String> keyIterator = keys.iterator();

                    // make CursorEntry a "service" by creating a trivial anonymous sub-class
                    CursorEntry<String, DBObject> entry = new CursorEntry(this.Map) {};

                    @Override
                    conditional Entry<String, DBObject> next() {
                        if (String key := keyIterator.next()) {
                            return True, entry.advance(key);
                        }
                        return False;
                    }
                };
            }

            @Override
            @Lazy Set<String> keys.calc() = infos.keys;

            @Override
            @Lazy Collection<DBObject> values.get() = new MapValues<String, DBObject>(this);

            @Override
            @Lazy Collection<Entry<String, DBObject>> entries.get() = new MapEntries<String, DBObject>(this);

            @Lazy Map<String, DboInfo> infos.calc() {
                Int[] childIds = info_.childIds;
                Int   size     = childIds.size;
                if (size == 0) {
                    return [];
                }

                import ecstasy.maps.CollectImmutableMap;
                static CollectImmutableMap<String, DboInfo> collector = new CollectImmutableMap();
                return childIds.associate(i -> {val info = infoFor(i); return info.name, info;}, collector);
            }
        };

        @Override
        <Result extends immutable Const> Result require(function Result(DBObjectImpl) test) {
            Transaction tx = requireTransaction_("require()");
            return outer.txManager.registerRequirement(tx.writeId_, info_.id, test);
        }

        @Override
        void defer(function Boolean(DBObjectImpl) adjust) {
            Transaction tx = requireTransaction_("defer()");
            Deferred_ deferred = new Deferred_(this, adjust);
            tx.root_.txAddDeferred_(deferred);
            dboAddDeferred_(deferred);
        }

        /**
         * Add a deferred adjustment to this DBObjectImpl's list of deferred adjustments.
         */
        protected void dboAddDeferred_(Deferred_ deferred) {
            if (Deferred_ dboLastDeferred ?= dboLastDeferred_) {
                dboLastDeferred.dboNextDeferred = deferred;
                dboLastDeferred_ = deferred;
            } else {
                dboFirstDeferred_ = deferred;
                dboLastDeferred_  = deferred;
            }
        }

        /**
         * Process all of this DBObjectImpl's list of deferred adjustments.
         *
         * @return True iff no deferred adjustment reported a failure
         */
        protected Boolean dboProcessDeferred_() {
            while (Deferred_ deferred ?= dboFirstDeferred_) {
                dboFirstDeferred_ = deferred.dboNextDeferred;

                if (function Boolean(DBObjectImpl) adjust ?= deferred.adjust) {
                    // wipe out the deferred work (so it doesn't accidentally get re-run in the
                    // future)
                    deferred.adjust = Null;

                    Boolean failure = False;
                    try {
                        if (!adjust(this)) {
                            failure = True;
                        }
                    } catch (Exception e) {
                        failure = True;

                        // log the exception (otherwise the information would be lost)
                        log($|While attempting to execute a deferred adjustment on \
                             |{dbPath.toString().substring(1)}, an exception occurred: {e}
                           );
                    }

                    if (failure) {
                        // mark the transaction as rollback-only
                        assert Transaction tx ?= outer.currentTx;
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
        protected void dboResetDeferred_() {
            dboFirstDeferred_ = Null;
            dboLastDeferred_  = Null;
        }

        /**
         * Require both that this DBObject be transactional, and that a transaction already exist.
         *
         * @param method  the method name or description to display in the assertion message
         */
        Transaction requireTransaction_(String method) {
            // this DBObject must be transactional
            assert this.transactional as $|DBObject {dbPath.toString().substring(1)} is not a \
                                          |transactional object; {method} requires a transaction
                                         ;

            // there must already be a transaction
            if (Transaction tx ?= outer.currentTx) {
                return tx;
            } else if (outer.conn == Null || outer.catalog.status != Running) {
                throw new DBClosed();
            } else {
                assert as "No transaction exists; {method} requires a transaction";
            }
        }

        /**
         * Perform all configured validation checks on this DBObject.
         *
         * @return True if the validation succeeded
         */
        Boolean validate_() {
            // TODO can/should an instance be cached?
            TxChange change = new TxChange_();

            for (val validator : info_.validators) {
                try {
                    if (!validator.validate(change)) {
                        return False;
                    }
                } catch (Exception e) {
                    this.Client.log($|An exception occurred while evaluating Validator \
                                     |"{&validator.class.displayName}": {e}
                                     );
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
        Boolean rectify_() {
            TxChange change = new TxChange_();

            for (val rectifier : info_.rectifiers) {
                try {
                    if (!rectifier.rectify(change)) {
                        return False;
                    }
                } catch (Exception e) {
                    this.Client.log($|An exception occurred while processing Rectifier \
                                     |"{&rectifier.class.displayName}": {e}
                                     );
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
        Boolean distribute_() {
            TxChange change = new TxChange_();

            for (val distributor : info_.distributors) {
                try {
                    if (!distributor.process(change)) {
                        return False;
                    }
                } catch (Exception e) {
                    this.Client.log($|An exception occurred while processing Distributor \
                                     |"{&distributor.class.displayName}": {e}
                                     );
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
                implements TxChange {
            @Override
            @Lazy DBObject pre.calc() {
                return ensurePreTxClient().implFor(info_.id);
            }

            @Override
            DBObject post.get() = outer;
        }
    }

    // ----- DBSchema ------------------------------------------------------------------------------

    /**
     * The DBSchema DBObject implementation.
     */
    class DBSchemaImpl(DboInfo info_)
            extends DBObjectImpl(info_)
            implements DBSchema {
        @Override
        @RO DBSchema? dbParent.get() = info_.id == 0 ? Null : super().as(DBSchema);
    }

    // ----- RootSchema ----------------------------------------------------------------------------

    /**
     * The RootSchema DBObject implementation.
     */
    class RootSchemaImpl(DboInfo info_)
            extends DBSchemaImpl(info_)
            implements RootSchema {
        @Override
        SystemSchema sys.get() = this.Client.implFor(BuiltIn.Sys.id).as(SystemSchema);
    }

    // ----- SystemSchema --------------------------------------------------------------------------

    /**
     * The SystemSchema DBObject implementation.
     */
    class SystemSchemaImpl(DboInfo info_)
            extends DBSchemaImpl(info_)
            implements SystemSchema {
        @Override
        @RO DBValue<DBInfo> info.get() = implFor(BuiltIn.Info.id).as(DBValue<DBInfo>);

        @Override
        @RO DBMap<String, DBUser> users.get() = implFor(BuiltIn.Users.id).as(DBMap<String, DBUser>);

        @Override
        @RO DBMap<String, Type> types.get() = implFor(BuiltIn.Types.id).as(DBMap<String, Type>);

        @Override
        @RO DBMap<String, DBObjectInfo> objects.get() = implFor(BuiltIn.Objects.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> schemas.get() = implFor(BuiltIn.Schemas.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> counters.get() = implFor(BuiltIn.Counters.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> values.get() = implFor(BuiltIn.Values.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> maps.get() = implFor(BuiltIn.Maps.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> lists.get() = implFor(BuiltIn.Lists.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> queues.get() = implFor(BuiltIn.Queues.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> processors.get() = implFor(BuiltIn.Processors.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBMap<String, DBObjectInfo> logs.get() = implFor(BuiltIn.Logs.id).as(DBMap<String, DBObjectInfo>);

        @Override
        @RO DBList<Pending> pending.get() = implFor(BuiltIn.Pending.id).as(DBList<Pending>);

        @Override
        @RO DBLog<DBTransaction> transactions.get() = implFor(BuiltIn.Transactions.id).as(DBLog<DBTransaction>);

        @Override
        @RO DBLog<String> errors.get() = implFor(BuiltIn.Errors.id).as(DBLog<String>);

        // TODO /sys/ stuff
        // @Override
        // DBInfo get() {
        //     return new DBInfo(
        //             name     = catalog.dir.toString(),
        //             version  = catalog.version ?: assert,
        //             created  = catalog.statusFile.created,
        //             modified = catalog.statusFile.modified,
        //             accessed = catalog.dir.accessed.maxOf(catalog.statusFile.modified),
        //             readable = catalog.statusFile.readable,
        //             writable = catalog.statusFile.writable && !catalog.readOnly,
        //             size     = catalog.dir.size);
        // }
    }

    // ----- Connection ----------------------------------------------------------------------------

    /**
     * The Connection API, for providing to a database client.
     */
    class Connection(DboInfo info_)
            extends RootSchemaImpl(info_)
            implements oodb.Connection<Schema> {

        @Override
        @RO DBUser dbUser.get() = outer.dbUser;

        @Override
        @RO (Transaction + Schema)? transaction.get() = outer.currentTx;

        /**
         * @param allowOnInternal  allow this operation to occur on an "internal" connection
         */
        @Override
        (Transaction + Schema) createTransaction(UInt?                  id              = Null,
                                                 String?                name            = Null,
                                                 DBTransaction.Priority priority        = Normal,
                                                 Boolean                readOnly        = False,
                                                 Duration?              timeout         = Null,
                                                 Int                    retryCount      = 0,
                                                 Boolean                allowOnInternal = False,
                                                ) {
            (Transaction + Schema)? oldTx = outer.currentTx;
            (Transaction + Schema)  newTx;
            if (oldTx == Null) {
                assert !internal || allowOnInternal;

                id ?:= outer.txManager.generateTxId();
                TxInfo txInfo = new TxInfo(id, name, priority, readOnly, timeout, retryCount);

                newTx = new RootTransaction(info_, txInfo).as(Transaction + Schema);
                outer.rootTx = newTx;
            } else {
                newTx = oldTx.createTransaction().as(Transaction + Schema);
            }
            return newTx;
        }

        @Override
        Connection clone() = this.Client.catalog.createConnection(dbUser).as(Connection);

        @Override
        void close(Exception? e = Null) {
            super(e);
            outer.conn   = Null;
            outer.rootTx = Null;
            notifyOnClose?(this.Client);
        }
    }

    // ----- Transaction ---------------------------------------------------------------------------

    /**
     * An internal base class for the non-nested [RootTransaction] and the [NestedTransaction]
     * virtual child classes.
     */
    protected @Abstract class Transaction(DboInfo info_)
            extends RootSchemaImpl(info_)
            implements oodb.Transaction<Schema> {
        /**
         * Outer transaction, aka "the parent".
         */
        @RO Transaction!? parent_;

        /**
         * Inner transaction, aka "the child".
         */
        public/protected NestedTransaction!? child_;

        /**
         * The [RootTransaction].
         */
        @RO RootTransaction root_;

        /**
         * The [RootTransaction] id.
         */
        @RO Int writeId_;

        /**
         * Notification of an inner transaction having closed.
         *
         * @param child  the child transaction that closed, which may or may not be the current
         *               child transaction
         */
        protected void childClosed_(NestedTransaction child) {
            if (&child == &child_) {
                child_ = Null;
            }
        }

        @Override
        @RO (Connection + Schema) connection.get() {
            // note: this is considered to be a valid request, regardless of whether this
            // transaction is a currently valid transaction or not
            return outer.conn ?: throw new DBClosed();
        }

        @Override
        (oodb.Transaction<Schema> + Schema) createTransaction() {
            assert pending as "Transaction is not active";

            if (val child ?= child_) {
                child.close();
                child_ = Null;
            }

            oodb.Transaction<Schema> result = new NestedTransaction(this);
            assert result.is(Schema);

            child_ = result;
            return result;
        }
    }

    /**
     * The Transaction API, for providing to a database client.
     */
    class RootTransaction(DboInfo info_, TxInfo txInfo)
            extends Transaction {

        /**
         * @param info_    the [DboInfo] for the [RootSchema]
         * @param txInfo   the externally visible information about the transaction
         * @param writeId  the internal "write id" for the transaction
         */
        construct(DboInfo info_, TxInfo txInfo, Int? writeId=Null) {
            construct Transaction(info_);
            this.txInfo = txInfo;
        } finally {
            writeId_ = writeId ?: txManager.begin(this, worker, readOnly || txInfo.readOnly);
        }

        @Override
        Transaction? parent_.get() = Null;

        @Override
        RootTransaction root_.get() = this;

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
         * @param txInfo   the externally visible information about the transaction
         * @param writeId  the internal "write id" for the transaction
         */
        void represent_(TxInfo txInfo, Int writeId) {
            this.txInfo   = txInfo;
            this.writeId_ = writeId;
        }

        /**
         * The transaction ID assigned to this transaction by the TxManager.
         *
         * Internally, this ID is known as the transaction's "write id". This id has no relation to
         * the application-specified transaction id that is held in the TxInfo, and has no meaning
         * within the database.
         */
        @Override
        public/protected Int writeId_ = NO_TX;

        @Override
        public/protected TxInfo txInfo;

        @Override
        @RO Boolean pending.get() = &this == outer.&rootTx;

        @Override
        @RO Boolean nested.get() = False;

        @Override
        Boolean rollbackOnly.set(Boolean value) {
            assert pending;
            assert value || !get();
            super(value);
        }

        /**
         * @param allowOnInternal  allow this operation to occur on an "internal" connection
         */
        @Override
        CommitResult commit(Boolean allowOnInternal = False) {
            RootTransaction? that = outer.rootTx;
            if (that == Null) {
                log($"Attempt to commit a previously closed transaction {this}; no current transaction.");
                return PreviouslyClosed;
            }

            if (&this != &that) {
                log($"Attempt to commit a previously closed transaction {this}; a different transaction is now in progress.");
                return PreviouslyClosed;
            }

            if (outer.internal && !allowOnInternal) {
                log($"Illegal commit request for {this}.");
                // technically, this error is not correct, but the gist is correct: this transaction
                // is not allowed to be committed
                return RollbackOnly;
            }

            // a transaction with a NO_TX id or a ReadId hasn't done anything to commit
            CommitResult result = Committed;
            if (writeId_ != NO_TX && TxManager.txCat(writeId_) == Open) {
                if (!txProcessDeferred_()) {
                    return DeferredFailed;
                }

                try {
                    result = txManager.commit(writeId_);
                } catch (DBClosed e) {
                    throw e;
                } catch (Exception e) {
                    log($"Exception during commit of {this}: {e}");
                    result = DatabaseError;
                    try {
                        txManager.rollback(writeId_);
                    } catch (Exception ignore) {}
                } finally {
                    // clearing out the transaction reference will "close" the transaction; it
                    // becomes no longer reachable internally
                    outer.rootTx = Null;
                }
            }

            close();
            return result;
        }

        /**
         * @param allowOnInternal  allow this operation to occur on an "internal" connection
         */
        @Override
        Boolean rollback(Boolean allowOnInternal = False) {
            RootTransaction? that = outer.rootTx;
            if (that == Null) {
                log($"Attempt to roll back a previously closed transaction {this}; no current transaction.");
                return False;
            }

            if (&this != &that) {
                log($"Attempt to roll back a previously closed transaction {this}; a different transaction is now in progress.");
                return False;
            }

            if (outer.internal && !allowOnInternal) {
                log($"Illegal rollback request for {this}.");
                return False;
            }

            Boolean result = True;
            if (writeId_ != NO_TX) {
                try {
                    result = txManager.rollback(writeId_);
                } catch (DBClosed e) {} catch (Exception e) {
                    log($"Exception during rollback of {this}: {e}");
                    result = False;
                } finally {
                    outer.rootTx = Null;
                }
            }

            close();
            return result;
        }

        @Override
        void close(Exception? e = Null) {
            if (NestedTransaction child ?= child_) {
                child.close();
                child_ = Null;
            }

            Transaction? that = outer.rootTx;
            if (&this == &that) {
                txResetDeferred_();
                try {
                    // this needs to eventually make its way to the implementation of close() on the
                    // Transaction interface itself, which will decide to either commit or to roll
                    // back the transaction, in the case that the transaction is still open
                    super(e);
                } finally {
                    outer.rootTx = Null;
                }
            }
        }

        /**
         * Add a deferred adjustment to this RootTransaction's list of deferred adjustments.
         */
        protected void txAddDeferred_(Deferred_ deferred) {
            if (Deferred_ txLastDeferred ?= txLastDeferred_) {
                txLastDeferred.txNextDeferred = deferred;
                txLastDeferred_ = deferred;
            } else {
                txFirstDeferred_ = deferred;
                txLastDeferred_  = deferred;
            }
        }

        /**
         * Process all of this DBObjectImpl's list of deferred adjustments.
         *
         * @return True iff no deferred adjustment reported a failure
         */
        protected Boolean txProcessDeferred_() {
            // fast path - nothing is deferred
            if (txFirstDeferred_ == Null) {
                return True;
            }

            Boolean       failure = False;
            DBObjectImpl? prevDbo = Null;
            while (Deferred_ deferred ?= txFirstDeferred_) {
                txFirstDeferred_ = deferred.txNextDeferred;

                DBObjectImpl dbo = deferred.dbo;
                if (dbo != prevDbo) {
                    dbo.dboResetDeferred_();
                    prevDbo = dbo;
                }

                if (!failure, function Boolean(DBObjectImpl) adjust ?= deferred.adjust) {
                    // wipe out the deferred work (so it doesn't accidentally get re-run in the
                    // future)
                    deferred.adjust = Null;

                    try {
                        if (!adjust(dbo)) {
                            failure = True;
                        }
                    } catch (Exception e) {
                        failure = True;

                        // log the exception (otherwise the information would be lost)
                        log($|While attempting to execute a deferred adjustment on\
                             | {dbo.dbPath.toString().substring(1)}, an exception occurred: {e}
                           );
                    }
                }
            }

            txResetDeferred_();

            if (failure) {
                rollbackOnly = True;

                // deferred adjustments failed
                return False;
            }

            return True;
        }

        /**
         * Reset the linked list of Deferred items on this DBObjectImpl.
         */
        protected void txResetDeferred_() {
            if (DBObjectImpl.Deferred_ deferred ?= txFirstDeferred_) {
                DBObjectImpl? prevDbo = Null;
                do {
                    // minor optimization: if there are a bunch of deferred adjustments in a row for
                    // the same DBObject, then only call reset once
                    DBObjectImpl dbo = deferred.dbo;
                    if (dbo != prevDbo) {
                        dbo.dboResetDeferred_();
                        prevDbo = dbo;
                    }
                } while (deferred ?= deferred.txNextDeferred);

                txFirstDeferred_ = Null;
                txLastDeferred_  = Null;
            }
        }

        @Override
        String toString() = $"Transaction(id={writeId_}, txInfo={txInfo})";
    }

    /**
     * The Transaction API for a "nested" transaction, i.e. when [Connection.createTransaction] is
     * called and a [Transaction] already exists. These are not nested transactions in the
     * traditional sense, in which rolling back a nested transaction does **not** impact the
     * containing transaction; instead, these are veneers for simplifying the programming model,
     * such that a developer can "blindly" use the following construct:
     *
     *     using (con.createTransaction()) {
     *         // within this block, all db operations are performed within a transaction
     *         // ...
     *     }
     *
     * In other words, this construct exists primarily to provide the ability for that above code
     * to nest/recurse arbitrarily.
     */
    class NestedTransaction
            extends Transaction {

        construct(Transaction parent_) {
            construct Transaction(parent_.info_);
            this.parent_ = parent_;
        }

        @Override
        @Final Transaction parent_;

        @Override
        @RO RootTransaction root_.get() = parent_.root_;

        @Override
        Int writeId_.get() = root_.writeId_;

        protected Boolean active_.get() = parent_.&child_ == &this && parent_.pending;

        protected Boolean completed_.set(Boolean value) {
            if (value) {
                child_ = Null;
            }
            super(value);
        }

        // ----- Transaction API -------------------------------------------------------------------

        @Override
        TxInfo txInfo.get() = root_.txInfo;

        @Override
        @RO Boolean pending.get() = !completed_ && active_;

        @Override
        @RO Boolean nested.get() = True;

        @Override
        Boolean rollbackOnly {
            @Override
            Boolean get() = super() || parent_.rollbackOnly;

            @Override
            void set(Boolean value) {
                if (value != get()) {
                    assert value   as "Nested Transaction rollbackOnly property cannot be reset";
                    assert pending as "Nested Transaction is not pending";
                    super(True);
                    parent_.rollbackOnly = True;
                }
            }
        }

        @Override
        CommitResult commit() {
            if (!active_) {
                return PreviouslyClosed;
            }

            completed_ = True;
            return rollbackOnly ? RollbackOnly : PendingCommit;
        }

        @Override
        Boolean rollback() {
            if (pending) {
                rollbackOnly = True;
                completed_   = True;
            }
            return rollbackOnly;
        }

        @Override
        void close(Exception? e = Null) {
            try {
                if (!completed_) {
                    super(e);
                }
            } finally {
                parent_.childClosed_(this);
            }
        }
    }

    // ----- DBValue -------------------------------------------------------------------------------

    /**
     * The DBValue implementation.
     */
    class DBValueImpl<Value extends immutable Const>(DboInfo info_, ValueStore<Value> store_)
            extends DBObjectImpl(info_)
            implements DBValue<Value> {

        protected ValueStore<Value> store_;

        @Override
        Value get() {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                return store_.load(txc.writeId);
            }
        }

        @Override
        void set(Value value) {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                store_.store(txc.writeId, value);
            }
        }
    }

    // ----- DBCounter -----------------------------------------------------------------------------

    /**
     * The DBCounter DBObject implementation.
     */
    class DBCounterImpl
            extends DBValueImpl<Int>
            implements DBCounter {

        construct(DboInfo info_, CounterStore store_) {
            construct DBValueImpl(info_, store_);
        }

        @Override
        @RO CounterStore store_.get() = super().as(CounterStore);

        @Override
        Boolean transactional.get() = info_.transactional;

        @Override
        Int next(Int count = 1) {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                return store_.adjust(txc.writeId, count);
            }
        }

        @Override
        void adjustBy(Int value) {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                store_.adjustBlind(txc.writeId, value);
            }
        }

        @Override
        Int preIncrement() {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                (_, Int after) = store_.adjust(txc.writeId, 1);
                return after;
            }
        }

        @Override
        Int preDecrement() {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                (_, Int after) = store_.adjust(txc.writeId, -1);
                return after;
            }
        }

        @Override
        Int postIncrement() {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                return store_.adjust(txc.writeId, 1);
            }
        }

        @Override
        Int postDecrement() {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                return store_.adjust(txc.writeId, -1);
            }
        }
    }

    // ----- DBMap ---------------------------------------------------------------------------------

    /**
     * The DBMap implementation.
     */
    class DBMapImpl<Key extends immutable Const, Value extends immutable Const>
            (DboInfo info_, MapStore<Key, Value> store_)
            extends DBObjectImpl(info_)
            implements DBMap<Key, Value>
            incorporates KeySetBasedMap<Key, Value> {

        protected MapStore<Key, Value> store_;

        @Override
        @RO Int size.get() {
            using (val txc = ensureTxContext(this)) {
                return store_.sizeAt(txc.writeId);
            }
        }

        @Override
        @RO Boolean empty.get() {
            using (val txc = ensureTxContext(this)) {
                return store_.emptyAt(txc.writeId);
            }
        }

        @Override
        Boolean contains(Key key) {
            using (val txc = ensureTxContext(this)) {
                return store_.existsAt(txc.writeId, key);
            }
        }

        @Override
        conditional Value get(Key key) {
            using (val txc = ensureTxContext(this)) {
                return store_.load(txc.writeId, key);
            }
        }

        @Override
        DBMapImpl put(Key key, Value value) {
            using (val txc = ensureTxContext(this)) {
                store_.store(txc.writeId, key, value);
                return this;
            }
        }

        @Override
        DBMapImpl putAll(Map<Key, Value> map) {
            using (val txc = ensureTxContext(this)) {
                for ((Key key, Value value) : map) {
                    store_.store(txc.writeId, key, value);
                }
                return this;
            }
        }

        @Override
        DBMapImpl remove(Key key) {
            using (val txc = ensureTxContext(this)) {
                store_.delete(txc.writeId, key);
                return this;
            }
        }

        @Override
        @Lazy public/private Set<Key> keys.calc() = new KeySet();

        /**
         * A representation of the Keys from the MapStore.
         */
        protected class KeySet
                implements Set<Key> {

            @Override
            Int size.get() = outer.size;

            @Override
            Boolean empty.get() = outer.empty;

            @Override
            Iterator<Element> iterator() = new KeyIterator();

            /**
             * An iterator over the keys in the MapStore.
             */
            protected class KeyIterator
                    implements Iterator<Key>
                    implements Closeable {
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
                protected/private Boolean finished.set(Boolean done) {
                    if (done) {
                        // make sure that the iterator has been marked as having started
                        started = True;
                    }

                    super(done);
                }

                @Override
                conditional Element next() {
                    while (True) {
                        if (nextIndex < keyBlock.size) {
                            return True, keyBlock[nextIndex++];
                        }

                        if (started && cookie == Null || finished) {
                            close();
                            return False;
                        }

                        // if there is no transaction, then we'll be creating an auto-commit
                        // transaction, but if we already have a cookie left over from a
                        // previous partial iteration, we can't guarantee that we will get the
                        // same read tx-id from the new autocommit transaction
                        assert !started || this.Client.rootTx != Null as
                                \|Unable to complete iteration in auto-commit mode;
                                 | the Map contained too many keys at the start of the iteration
                                 | to deliver them within a single autocommit transaction.
                                ;

                        // load the next (or the first) block of keys
                        started = True;
                        using (val txc = ensureTxContext(this.DBMapImpl)) {
                            (keyBlock, cookie) = store_.keysAt(txc.writeId, cookie);
                            nextIndex = 0;
                        }
                    }
                }

                @Override
                Boolean knownDistinct() = True;

                @Override
                conditional Int knownSize() {
                    if (!started) {
                        return True, outer.size;
                    }

                    if (finished) {
                        return True, 0;
                    }

                    return False;
                }

                @Override
                void close(Exception? cause = Null) {
                    // idempotent clean-up, and clear all unnecessary references
                    finished  = True;
                    keyBlock  = [];
                    nextIndex = 0;
                    cookie    = Null;
                }
            }

            @Override
            Boolean contains(Key key) = outer.contains(key);

            @Override
            KeySet remove(Key key) {
                outer.remove(key);
                return this;
            }
        }

        /**
         * An implementation of the Entry interface suitable for use as the "original" entry.
         */
        protected const HistoricalEntry
                extends DiscreteEntry<Key, Value>
                implements DBMap.Entry<Key, Value> {

            construct(Key key) {
                construct DiscreteEntry(key, readOnly=True);
            }

            construct(Key key, Value value) {
                construct DiscreteEntry(key, value, readOnly=True);
            }

            @Override
            DBMap.Entry<Key, Value> original.get() = this;
        }
    }

    // ----- DBMap (for sys schema) ----------------------------------------------------------------

    /**
     * The DBMap implementation for the maps in the system schema.
     */
    class SysMapImpl<Value extends immutable Const>(DboInfo info, DBCategory category, Catalog catalog)
            extends DBObjectImpl(info)
            implements DBMap<String, Value>
            incorporates KeySetBasedMap<String, Value> {

        @Lazy Int[] ids_.calc() {
            DBCategory category = this.category;
            return catalog.metadata?.dbObjectInfos
                    .filter(info -> info.category == category)
                    .map(info -> info.id, CollectImmutableArray.of(Int)) : assert;
        }

        @Lazy
        Map<String, Int> pathToId_.calc() {
            if (empty) {
                return [];
            }

            HashMap<String, Int> map = new HashMap();
            for (Int id : ids_) {
                DboInfo info = infoFor(id);
                map.put(info.path.toString(), id);
            }
            return map.freeze(inPlace=True);
        }

        @Override
        @RO Int size.get() = ids_.size;

        @Override
        @RO Boolean empty.get() = ids_.empty;

        @Override
        Boolean contains(Key key) = pathToId_.contains(key);

        @Override
        conditional Value get(Key key) {
            if (Int id := pathToId_.get(key)) {
                return True, infoFor(id).as(Value);
            }

            return False;
        }

        @Override
        Set<Key> keys.get() = pathToId_.keys;

        @Override
        SysMapImpl put(Key key, Value value) = throw new ReadOnly();

        @Override
        SysMapImpl remove(Key key) = throw new ReadOnly();
    }


    // ----- DBLog ---------------------------------------------------------------------------------

    /**
     * The DBLog implementation.
     */
    class DBLogImpl<Value extends immutable Const>(DboInfo info_, LogStore<Value> store_)
            extends DBObjectImpl(info_)
            implements DBLog<Value> {

        protected LogStore<Value> store_;

        @Override
        Boolean transactional.get() = info_.transactional;

        @Override
        DBLogImpl add(Value value) {
            using (val txc = ensureTxContext(this, allowNonTx=True)) {
                store_.append(txc.writeId, value);
            }
            return this;
        }

        @Override
        conditional List<Entry> select((Range<Time>|Duration)? period = Null,
                                       DBUser?                 user   = Null,
                                       (UInt|Range<UInt>)?     txIds  = Null,
                                       String?                 txName = Null) {
            // TODO implement this feature
            return False;
        }
    }

    // ----- DBProcessor ---------------------------------------------------------------------------

    /**
     * The DBProcessor implementation.
     */
    class DBProcessorImpl<Message extends immutable Const>(DboInfo info_, ProcessorStore<Message> store_)
            extends DBObjectImpl(info_)
            implements DBProcessor<Message> {

        protected ProcessorStore<Message> store_;

        @Override
        void schedule(Message message, Schedule? when=Null) {
            using (val txc = ensureTxContext(this)) {
                store_.schedule(txc.writeId, message, when);
            }
        }

        @Override
        void scheduleAll(Iterable<Message> messages, Schedule? when=Null) {
            using (val txc = ensureTxContext(this)) {
                super(messages, when);
            }
        }

        @Override
        void reschedule(Message message, Schedule when) {
            using (val txc = ensureTxContext(this)) {
                super(message, when);
            }
        }

        @Override
        void unschedule(Message message) {
            using (val txc = ensureTxContext(this)) {
                return store_.unschedule(txc.writeId, message);
            }
        }

        @Override
        void unscheduleAll() {
            using (val txc = ensureTxContext(this)) {
                store_.unscheduleAll(txc.writeId);
            }
        }

        /**
         * Implements the list as returned from the pending() method.
         */
        class PendingList_(Int txid, Int[] pids)
                implements List<Pending> {

            /**
             * Cached Pending objects; the contents of this list.
             */
            Pending?[] pendingCache = new Pending?[];

            /**
             * Checks to make sure that this list isn't used outside of its transaction.
             */
            void checkTx() {
                assert txid == this.Client.rootTx?.writeId_ : False;
            }

            @Override
            Int size.get() {
                checkTx();
                return pids.size;
            }

            @Override
            @Op("[]") Pending getElement(Index index) {
                checkTx();
                assert:bounds 0 <= index < pids.size;
                if (index < pendingCache.size, Pending pending ?= pendingCache[index]) {
                    return pending;
                }

                Pending pending = store_.pending(txid, pids[index]);

                // cache the result
                pendingCache[index] = pending;

                return pending;
            }

            @Override
            List<Pending> reify() {
                checkTx();
                return toArray();
            }
        }

        @Override
        List<Pending> pending() {
            using (val txc = ensureTxContext(this)) {
                Int[] pids = store_.pidListAt(txc.writeId);
                List<Pending> list = new PendingList_(txc.writeId, pids);
                return txc.autocommit ? list.reify() : list;
            }
        }

        /**
         * Process one pending message.
         *
         * @param pendingId  indicates which Pending
         * @param message    the Message to process
         *
         * @return the elapsed processing time
         * @return the exceptional processing failure, iff the processing failed
         */
        (Range<Time>, Exception?) process_(Int pendingId, Message message) {
            Transaction tx      = requireTransaction_("process()");
            Exception?  failure = Null;
            Time        start   = clock.now;
            Range<Time> elapsed;
            try {
                process(message);
            } catch (Exception e) {
                failure = e;
            }
            elapsed = start..clock.now;

            if (failure == Null) {
                // the information about the PID being successfully processed is part of the
                // transactional record, and must be committed as part of this transaction, so
                // the information is provided to the DBProcessor's ObjectStore at this point;
                // if the transaction fails to commit, then the fact that the PID was processed
                // will also be lost -- as it should be!
                store_.processCompleted(tx.writeId_, message, pendingId, elapsed);
            }

            return elapsed, failure;
        }

        @Override
        void process(Message message) {
            // this method must be overridden; the database schema is invalid if a DBProcessor
            // does not override this method
        }

        @Override
        void processAll(List<Message> messages) {
            using (val txc = ensureTxContext(this)) {
                super(messages);
            }
        }

        @Override
        Boolean autoRetry(Message                  message,
                          CommitResult | Exception result,
                          Schedule?                when,
                          Range<Time>              elapsed,
                          Int                      timesAttempted) {
            using (val txc = ensureTxContext(this)) {
                return super(message, result, when, elapsed, timesAttempted);
            }
        }

        /**
         * Notification of a decision to retry.
         */
        void retrying_(Message                  message,
                       Int                      pendingId,
                       Range<Time>              elapsed,
                       CommitResult | Exception result) {
            Transaction tx = requireTransaction_("retryPending()");
            store_.retryPending(tx.writeId_, message, pendingId, elapsed, result);
        }

        /**
         * Notification of a decision to abandon.
         */
        void abandoning_(Message                  message,
                         Int                      pendingId,
                         Range<Time>              elapsed,
                         CommitResult | Exception result) {
            Transaction tx = requireTransaction_("abandonPending()");
            store_.abandonPending(tx.writeId_, message, pendingId, elapsed, result);
        }

        @Override
        void abandon(Message                  message,
                     CommitResult | Exception result,
                     Schedule?                when,
                     Range<Time>              elapsed,
                     Int                      timesAttempted) {
            using (val txc = ensureTxContext(this)) {
                super(message, result, when, elapsed, timesAttempted);
            }
        }

        @Override
        void suspend() {
            using (val txc = ensureTxContext(this)) {
                store_.setEnabled(txc.writeId, False);
            }
        }

        @Override
        Boolean suspended.get() {
            using (val txc = ensureTxContext(this)) {
                return store_.isEnabled(txc.writeId);
            }
        }

        @Override
        void resume() {
            using (val txc = ensureTxContext(this)) {
                store_.setEnabled(txc.writeId, True);
            }
        }
    }
}