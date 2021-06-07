import ecstasy.collections.maps.KeySetBasedMap;
import ecstasy.reflect.Annotation;

import json.Doc;
import json.Lexer.Token;
import json.Mapping;

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
import oodb.NoTx;
import oodb.RootSchema;
import oodb.SystemSchema;

import model.DBObjectInfo;

import storage.MapStore;
import storage.ObjectStore;
import storage.ValueStore;

import Catalog.BuiltIn;


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
 * together the OODB API with the API and type system defined by the custom database.
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
    construct(Catalog<Schema> catalog, Int id, DBUser dbUser, Boolean readOnly = False, function void(Client)? notifyOnClose = Null)
        {
        assert Schema == RootSchema || catalog.metadata != Null;

        this.id            = id;
        this.dbUser        = dbUser;
        this.catalog       = catalog;
        this.jsonSchema    = catalog.jsonSchema;
        this.readOnly      = readOnly || catalog.readOnly;
        this.notifyOnClose = notifyOnClose;
        }
    finally
        {
        reentrancy = Exclusive;
        conn       = new Connection(infoFor(0)).as(Connection + Schema);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The JSON db catalog, representing the database on disk.
     */
    public/private Catalog<Schema> catalog;

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
     * The base transaction ID used by this client, based on the current transaction (or based on
     * the transaction manager, if no transaction is existent).
     */
    Int baseTxId.get()
        {
        return tx?.baseId_ : TxManager.USE_LAST_TX;
        }

    /**
     * The lazily created application DBObjects within the schema.
     */
    protected/private DBObjectImpl?[] appObjects = new DBObjectImpl[];

    /**
     * The lazily created system schema DBObjects.
     */
    protected/private DBObjectImpl?[] sysObjects = new DBObjectImpl[];

    /**
     * The function to use to notify that the connection has closed.
     */
    protected function void(Client)? notifyOnClose;


    // ----- support ----------------------------------------------------------------------------

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
     * Obtain the current transaction, creating one if necessary.
     *
     * @return the Transaction
     * @return True if the caller is responsible for committing the returned transaction after using
     *         it
     */
    (Transaction + Schema tx, Boolean autocommit) ensureTransaction()
        {
        checkRead();

        var     tx         = this.tx;
        Boolean autocommit = False;

        if (tx == Null)
            {
            tx         = (conn?: assert).createTransaction(name="autocommit");
            autocommit = True;
            }

        return tx, autocommit;
        }

    /**
     * For the transaction manager's reserved "singleton" transaction that it uses to represent the
     * original state of a transaction when executing trigger logic, set the "read level" (the base
     * transaction id).
     *
     * @param readId  the transaction id to use as a read level
     */
    void adjustBaseTransaction(Int readId)
        {
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
            case DBMap:      new DBMapImpl(info, storeFor(id).as(MapStore));
            case DBList:     TODO
            case DBQueue:    TODO
            case DBLog:      TODO
            case DBCounter:  TODO
            case DBValue:    new DBValueImpl(info, storeFor(id).as(ValueStore));
            case DBFunction: TODO
            };
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
     * The Worker virtual service child class exists to allow work to be delegated explicitly to
     * this service in order to be executed. This allows CPU-intensive (expensive) work to be dumped
     * back onto the Client instead of letting that work fall onto more critical services, such AS
     * the various `ObjectStore` services.
     */
    class Worker
        {
        import ecstasy.io.CharArrayReader;
        import json.ObjectInputStream;
        import json.ObjectOutputStream;

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
            return mapping.read(new ObjectInputStream(jsonSchema, new CharArrayReader(jsonText)).ensureElementInput());
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
        @RO String dbPath.get()
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

        // ----- transaction management ------------------------------------------------------------

//        Boolean txPrepare_()
//        txCommit_()
//        txReset_()
        }


    // ----- DBSchema ------------------------------------------------------------------------------

    /**
     * The DBSchema DBObject implementation.
     */
    class DBSchemaImpl(DBObjectInfo info_)
            extends DBObjectImpl(info_)
            incorporates NoTx
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
            assert outer.tx == Null;

            import Transaction.TxInfo;
            TxInfo txInfo = new TxInfo(timeout, name, id, priority, retryCount);

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
        /**
         * The transaction ID that this transaction is based from.
         */
        public/protected Int baseId_ = TxManager.USE_LAST_TX;

        /**
         * The IDs changes within the transaction. Key is the DBObject id, and the value is the
         * corresponding TxChange object. (This differs from DBTransaction, which uses the path as
         * the key of the map.)
         */
        protected Set<Int> pending_ = Set:[];

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
            if (that == Null)
                {
                throw new IllegalState(`|Attempt to commit a previously closed transaction;\
                                        | no current transaction.
                                      );
                }

            if (&this != &that)
                {
                throw new IllegalState(`|Attempt to commit a previously closed transaction;\
                                        | a different transaction is in progress.
                                      );
                }

            TODO

            close();
            }

        @Override
        void rollback()
            {
            Transaction? that = outer.tx;
            if (that == Null)
                {
                throw new IllegalState(`|Attempt to roll back a previously closed transaction;\
                                        | no current transaction.
                                      );
                }

            if (&this != &that)
                {
                throw new IllegalState(`|Attempt to roll back a previously closed transaction;\
                                        | a different transaction is in progress.
                                      );
                }

            close();
            }

        @Override
        void close(Exception? e = Null)
            {
            val that = outer.tx;
            if (&this == &that)
                {
                super(e);
                // TODO discard any tx data as well (where is it stored?)
                outer.tx = Null;
                }
            }
        }


    // ----- DBValue ---------------------------------------------------------------------------

    /**
     * The DBValue DBObject implementation.
     */
    class DBValueImpl<Value extends immutable Const>(DBObjectInfo info_, ValueStore<Value> store_)
            extends DBObjectImpl(info_)
            implements DBValue<Value>
        {
        protected ValueStore<Value> store_;

        @Override
        Value get()
            {
//            if ((Token[] tokens, val val) := store_.load(baseTxId))    // TODO why doesn't Value? work?
//                {
//                return val? : assert; // TODO deser tokens
//                }
//             return store_.initial;
TODO
            }

        @Override
        void set(Value value)
            {
            TODO impl
            }
        }


    // ----- DBCounter -----------------------------------------------------------------------------

    // TODO


    // ----- DBMap ---------------------------------------------------------------------------------

    /**
     * The DBMap DBObject implementation.
     *
     * TODO how to make the client responsible for ser/deser work? yet how to make the same cacheable?
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
            return store_.sizeAt(baseTxId);
            }

        @Override
        @RO Boolean empty.get()
            {
            return store_.emptyAt(baseTxId);
            }

        @Override
        Boolean contains(Key key)
            {
            return store_.existsAt(baseTxId, key);
            }

        @Override
        conditional Value get(Key key)
            {
            return store_.load(baseTxId, key);
            }

        @Override
        Set<Key> keys.get()
            {
            TODO
            }
        }
    }
