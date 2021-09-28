import ecstasy.collections.maps.KeySetBasedMap;
import ecstasy.reflect.Annotation;

import json.Doc;
import json.Lexer.Token;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectOutputStream;

import oodb.DBCounter;
import oodb.DBFunction;
import oodb.DBInfo;
import oodb.DBInvoke;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBQueue;
import oodb.DBSchema;
import oodb.DBTransaction;
import oodb.DBUser;
import oodb.DBValue;
import oodb.RootSchema;
import oodb.SystemSchema;
import oodb.Transaction.TxInfo;

import model.DBObjectInfo;

import storage.CounterStore;
import storage.LogStore;
import storage.MapStore;
import storage.ObjectStore;
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
        reentrancy = Exclusive;
        conn       = new Connection(infoFor(0)).as(Connection + Schema);
        worker     = new Worker(jsonSchema);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON db catalog, representing the database on disk.
     */
    public/private Catalog<Schema> catalog;

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
     * A read-only client that provides the "before transaction" view.
     */
    protected/private Client<Schema>? preTxView;

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
     * @param dbo  pass the DBObject instance if it is possible that it is non-transactional
     *
     * @return the transactional context object
     */
    TxContext ensureTransaction(DBObject? dbo = Null)
        {
        private TxContext ctx = new TxContext();
        private TxContext ntx = new NtxContext();

        checkRead();

        if (!dbo?.transactional)
            {
            return ntx;
            }

        var     tx         = this.tx;
        Boolean autocommit = False;

        if (tx == Null)
            {
            assert !internal;

            tx         = (conn?: assert).createTransaction(name="autocommit");
            autocommit = True;
            }

        ctx.enter(autocommit);
        return ctx;
        }

    /**
     * True if this is an internal ("system") client.
     */
    @RO Boolean internal.get()
        {
        return Catalog.isInternalClientId(id);
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
        static TxInfo rwInfo = new TxInfo(name="internal", readOnly=False);
        static TxInfo roInfo = new TxInfo(name="internal", readOnly=True );

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
     * Select the AsyncTriggers to run after this transaction has completed, based on the
     * changes to this DBObject.
     *
     * @param dboId  the id of the DBObject to select triggers for
     *
     * @return an array of Booleans, corresponding to the [DBObjectInfo.asyncTriggers] for the
     *         specified dboId, indicating which AsyncTrigger objects should be executed
     */
    immutable Boolean[] selectDBObjectTriggers(Int dboId)
        {
        return implFor(dboId).selectTriggers_();
        }

    /**
     * Execute the specified AsyncTrigger for this DBObject.
     *
     * @param dboId     the id of the DBObject to select triggers for
     * @param iTrigger  an index into the [DBObjectInfo.asyncTriggers] array for this DBObject,
     *                  indicating which AsyncTrigger to execute
     */
    void execDBObjectTrigger(Int dboId, Int iTrigger)
        {
        implFor(dboId).execTrigger_(iTrigger);
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
                case Maps:         TODO new DBMap<String, DBMap>();
                case Queues:       TODO new DBMap<String, DBQueue>();
                case Lists:        TODO new DBMap<String, DBList>();
                case Logs:         TODO new DBMap<String, DBLog>();
                case Counters:     TODO new DBMap<String, DBCounter>();
                case Values:       TODO new DBMap<String, DBValue>();
                case Functions:    TODO new DBMap<String, DBFunction>();
                case Pending:      TODO new DBList<DBInvoke>();
                case Transactions: TODO new DBLog<DBTransaction>();
                case Errors:       TODO new DBLog<String>();
                default: assert;
                };
            }

        DBObjectInfo info = infoFor(id);
        return switch (info.category)
            {
            case DBSchema:   new DBSchemaImpl(info);
            case DBMap:      createDBMapImpl(info, storeFor(id).as(MapStore));
            case DBList:     TODO
            case DBQueue:    TODO
            case DBLog:      createDBLogImpl  (info, storeFor(id).as(LogStore));
            case DBCounter:  new DBCounterImpl(info, storeFor(id).as(CounterStore));
            case DBValue:    createDBValueImpl(info, storeFor(id).as(ValueStore));
            case DBFunction: TODO
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
                    assert tx.commit() as "Failed to auto-commit the transaction";
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
         * Select the AsyncTriggers to run after this transaction has completed, based on the
         * changes to this DBObject.
         *
         * @return an array of Booleans, corresponding to the [DBObjectInfo.asyncTriggers] for this
         *         DBObject, indicating which AsyncTrigger objects should be executed
         */
        immutable Boolean[] selectTriggers_()
            {
            TxChange change = new TxChange_();

            AsyncTrigger[] available = info_.asyncTriggers;
            Boolean[]      selected  = new Boolean[available.size](False);
            Loop: for (val trigger : available)
                {
                try
                    {
                    if (trigger.appliesTo(change))
                        {
                        selected[Loop.count] = True;
                        }
                    }
                catch (Exception e)
                    {
                    this.Client.log($|An exception occurred in DBObject {info_.idString} while\
                                     | evaluating AsyncTrigger "{&trigger.actualClass.displayName}": {e}
                                   );
                    }
                }

            return selected.freeze(inPlace = True);
            }

        /**
         * Execute the specified AsyncTrigger for this DBObject.
         *
         * @param iTrigger  an index into the [DBObjectInfo.asyncTriggers] array for this DBObject,
         *                  indicating which AsyncTrigger to execute
         */
        void execTrigger_(Int iTrigger)
            {
            TxChange change = new TxChange_();

            val trigger = info_.asyncTriggers[iTrigger];
            try
                {
                trigger.process(change);
                }
            catch (Exception e)
                {
                this.Client.log($|An exception occurred in DBObject {info_.idString} while executing\
                                 | AsyncTrigger "{&trigger.actualClass.displayName}": {e}
                               );
                }
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
        @RO DBMap<String, DBMap> maps.get()
            {
            return implFor(BuiltIn.Maps.id).as(DBMap<String, DBMap>);
            }

        @Override
        @RO DBMap<String, DBQueue> queues.get()
            {
            return implFor(BuiltIn.Queues.id).as(DBMap<String, DBQueue>);
            }

        @Override
        @RO DBMap<String, DBList> lists.get()
            {
            return implFor(BuiltIn.Lists.id).as(DBMap<String, DBList>);
            }

        @Override
        @RO DBMap<String, DBLog> logs.get()
            {
            return implFor(BuiltIn.Logs.id).as(DBMap<String, DBLog>);
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
        @RO DBMap<String, DBFunction> functions.get()
            {
            return implFor(BuiltIn.Functions.id).as(DBMap<String, DBFunction>);
            }

        @Override
        @RO DBList<DBInvoke> pending.get()
            {
            return implFor(BuiltIn.Pending.id).as(DBList<DBInvoke>);
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
        (Transaction + Schema) createTransaction(Duration?              timeout     = Null,
                                                 String?                name        = Null,
                                                 UInt?                  id          = Null,
                                                 DBTransaction.Priority priority    = Normal,
                                                 Int                    retryCount  = 0,
                                                 Boolean                readOnly    = False)
            {
            assert outer.tx == Null as "Attempted to create a transaction when one already exists";
            assert !internal;

            TxInfo txInfo = new TxInfo(timeout, name, id, priority, retryCount, readOnly);

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
        Boolean commit()
            {
            Transaction? that = outer.tx;
            assert that != Null
                as "Attempt to commit a previously closed transaction; no current transaction.";
            assert &this == &that
                as" Attempt to commit a previously closed transaction; a different transaction is in progress.";
            assert !internal
                as "Illegal commit request during the prepare phase.";

            // clearing out the transaction reference will "close" the transaction; it becomes no
            // longer reachable internally
            outer.tx = Null;

            // a transaction with a NO_TX id or a ReadId hasn't done anything to commit
            Boolean success = True;
            if (TxManager.txCat(id_) == Open)
                {
                // it's important to prevent re-entrancy from the outside while this logical thread
                // of execution is wending its way through the transaction manager and the various
                // ObjectStores that are enlisted in the transaction; the Exclusive (instead of
                // Forbidden) mode is important, because work can still be delegated back to this
                // Client's Worker instance by the enlisted ObjectStores
                using (new CriticalSection(Exclusive))
                    {
                    success = txManager.commit(id_);
                    }
                }

            // close() is called only out of respect to any potential sub-classes
            close();

            return success;
            }

        @Override
        void rollback()
            {
            Transaction? that = outer.tx;
            assert that != Null
                as "Attempt to roll back a previously closed transaction; no current transaction.";
            assert &this == &that
                as" Attempt to roll back a previously closed transaction; a different transaction is in progress.";
            assert !internal
                as "Illegal rollback request during the prepare phase.";

            // (see implementation notes from commit() above)

            outer.tx = Null;

            if (id_ != NO_TX)
                {
                using (new CriticalSection(Exclusive))
                    {
                    txManager.rollback(id_);
                    }
                }

            close();
            }

        @Override
        void close(Exception? e = Null)
            {
            Transaction? that = outer.tx;
            if (&this == &that)
                {
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
        }


    // ----- DBValue ---------------------------------------------------------------------------

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
            using (val tx = ensureTransaction(this))
                {
                return store_.load(tx.id);
                }
            }

        @Override
        void set(Value value)
            {
            using (val tx = ensureTransaction(this))
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
            using (val tx = ensureTransaction(this))
                {
                return store_.adjust(tx.id, count);
                }
            }

        @Override
        void adjustBy(Int value)
            {
            using (val tx = ensureTransaction(this))
                {
                store_.adjustBlind(tx.id, value);
                }
            }

        @Override
        Int preIncrement()
            {
            using (val tx = ensureTransaction(this))
                {
                (_, Int after) = store_.adjust(tx.id, 1);
                return after;
                }
            }

        @Override
        Int preDecrement()
            {
            using (val tx = ensureTransaction(this))
                {
                (_, Int after) = store_.adjust(tx.id, -1);
                return after;
                }
            }

        @Override
        Int postIncrement()
            {
            using (val tx = ensureTransaction(this))
                {
                return store_.adjust(tx.id, 1);
                }
            }

        @Override
        Int postDecrement()
            {
            using (val tx = ensureTransaction(this))
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
            using (val tx = ensureTransaction())
                {
                return store_.sizeAt(tx.id);
                }
            }

        @Override
        @RO Boolean empty.get()
            {
            using (val tx = ensureTransaction())
                {
                return store_.emptyAt(tx.id);
                }
            }

        @Override
        Boolean contains(Key key)
            {
            using (val tx = ensureTransaction())
                {
                return store_.existsAt(tx.id, key);
                }
            }

        @Override
        conditional Value get(Key key)
            {
            using (val tx = ensureTransaction())
                {
                return store_.load(tx.id, key);
                }
            }

        @Override
        DBMapImpl put(Key key, Value value)
            {
            using (val tx = ensureTransaction())
                {
                store_.store(tx.id, key, value);
                return this;
                }
            }

        @Override
        DBMapImpl remove(Key key)
            {
            using (val tx = ensureTransaction())
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
                        using (val tx = ensureTransaction())
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


    // ----- DBLog ---------------------------------------------------------------------------

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
            using (val tx = ensureTransaction(this))
                {
                store_.append(tx.id, value);
                }
            return this;
            }
        }
    }
