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
 * This service is effectively abstract; it is expected that each database will involve the
 * production of a sub-class that adds an implementation of the root schema to the Connection and
 * Transaction virtual child classes.
 */
service Client<Schema extends RootSchema>
    {
    /**
     *
     */
    construct(Catalog<Schema> catalog, Int id, DBUser dbUser, function void(Client)? notifyOnClose = Null)
        {
        assert Schema == RootSchema || catalog.metadata != Null;

        this.id            = id;
        this.dbUser        = dbUser;
        this.catalog       = catalog;
        this.notifyOnClose = notifyOnClose;
        }
    finally
        {
        // TODO GG: reentracy = Exclusive;
        conn      = new Connection(infoFor(0)).as(Connection + Schema);
        }

    /**
     * The DBUser represented by this Client service.
     */
    public/private Catalog<Schema> catalog;

    /**
     * The id of this Client service.
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
    public Int baseTxId.get()
        {
        return tx?.baseId_ : TxManager.USE_LAST_TX;
        }

    /**
     * The lazily created application DBObjects within the schema.
     */
    protected/private DBObjectImpl?[] appObjects;

    /**
     * The lazily created system schema DBObjects.
     */
    protected/private DBObjectImpl?[] sysObjects;

    /**
     * The function to use to notify that the connection has closed.
     */
    protected function void(Client)? notifyOnClose;


    // ----- support ----------------------------------------------------------------------------

    Boolean check()
        {
        // TODO
        return True;
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
        // TODO

        DBObjectInfo info  = infoFor(id);
        return switch (BuiltIn.byId(info.id))
            {
            case Root:         new RootSchemaImpl(info);
            case Sys:          new SystemSchemaImpl(info);
            case Info:         TODO new DBValue<DBInfo>();
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
     *
     * TODO figure out what methods are actually needed / used
     */
    class Worker
        {
        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, String jsonText)
            {
            TODO
            }

        <Serializable> Serializable readUsing(Mapping<Serializable> mapping, Token[] jsonTokens)
            {
            TODO
            }

        <Serializable> String writeUsing(Mapping<Serializable> mapping, Serializable value)
            {
            TODO
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
        @Lazy Map<String, DBObject> dbChildren.calc()
            {
            return new Map()
                {
                @Override
                // TODO GG this doesn't work: conditional Value get(Key key)
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

        // some TODO items (the function thing) and optimizations
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
        // TODO sys property
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
                                                 Int                    retryCount  = 0)
            {
            TODO impl
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
    class Transaction(DBObjectInfo info_)
            extends RootSchemaImpl(info_)
            implements oodb.Transaction<Schema>
        {
        /**
         * The transaction ID that this transaction is based from.
         */
        public/protected Int baseId_;

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
            extends DBObjectImpl(info_) // TODO GG try without "extends immutable Const" to see hard to understand errors
            implements DBValue<Value>
        {
        protected ValueStore<Value> store_;

        @Override
        Value get()
            {
            if ((Token[] tokens, val val) := store_.load(baseTxId))    // TODO why doesn't Value? work?
                {
                return val? : assert; // TODO deser tokens
                }

            return store_.initial;
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
    class DBMapImpl<Key, Value>(DBObjectInfo info_, MapStore<Key, Value> store_)
            extends DBObjectImpl(info_) // TODO GG try without "extends immutable Const" to see hard to understand errors
            implements DBMap<Key, Value>
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
        }
    }
