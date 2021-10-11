import ecstasy.collections.maps.KeySetBasedMap;
import ecstasy.reflect.Annotation;

import oodb.DBCounter;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBPending;
import oodb.DBProcessor;
import oodb.DBQueue;
import oodb.DBSchema;
import oodb.DBTransaction;
import oodb.DBUser;
import oodb.DBValue;
import oodb.RootSchema;
import oodb.SystemSchema;

import storage.CounterStore;
import storage.MapStore;
import storage.ValueStore;

import Catalog.SchemaStore;


/**
 * The root of the in-memory database API, as exposed to applications. This provides an
 * implementation of the OODB Connection and Transaction interfaces.
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
     * @param id             the id assigned to this Client service
     * @param dbUser         the database user to create the client on behalf of
     * @param readOnly       (optional) pass True to indicate that client is not permitted to modify
     *                       any data
     * @param notifyOnClose  the function to call when the client connection is closed
     */
    construct(Int id, DBUser dbUser, Boolean readOnly = False,
              function void(Client)? notifyOnClose = Null)
        {
        assert Schema == RootSchema || Catalog.metadata != Null;

        this.id            = id;
        this.dbUser        = dbUser;
        this.readOnly      = readOnly;
        this.notifyOnClose = notifyOnClose;
        }
    finally
        {
        conn = new Connection(storeFor("").as(SchemaStore)).as(Connection + Schema);
        }


    // ----- properties ----------------------------------------------------------------------------

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
     * The map of DBObjects within the schema keyed by the 'id'.
     */
    protected/private Map<String, DBObjectImpl> dbObjects = new HashMap();

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
     * Obtain the current transaction, creating one if necessary, and wrapping it in a transactional
     * context.
     *
     * @param impl  the DBObjectImpl that should be enlisted into the transaction
     *
     * @return the transactional context object
     */
    TxContext ensureTransaction(DBObjectImpl impl)
        {
        checkRead();

        var     tx         = this.tx;
        Boolean autocommit = False;

        if (tx == Null)
            {
            tx         = (conn?: assert).createTransaction(name="autocommit");
            autocommit = True;
            }

        tx.enlist(impl);

        private TxContext ctx = new TxContext();
        ctx.init(tx, autocommit);
        return ctx;
        }

    /**
     * Join the current transaction.
     *
     * @param impl  the DBObjectImpl that should be enlisted into the transaction if there is one
     *
     * @return True if there is a "current" transaction
     */
    Boolean joinTransaction(DBObjectImpl impl)
        {
        val tx = this.tx;
        if (tx == Null)
            {
            return False;
            }

        tx.enlist(impl);
        return True;
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    DBObjectInfo infoFor(String id)
        {
        return Catalog.infoFor(id);
        }

    /**
     * Obtain the DBObjectImpl for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectImpl for the specified id
     */
    DBObjectImpl implFor(String id)
        {
        DBObjectImpl impl;
        if (impl := dbObjects.get(id))
            {
            return impl;
            }

        impl = createImpl(id);

        dbObjects.put(id, impl);

        return impl;
        }

    /**
     * Create an DBObjectImpl for the specified internal database object id.
     *
     * @param id  the internal object id
     *
     * @return the new DBObjectImpl
     */
    DBObjectImpl createImpl(String id)
        {
        DBObjectInfo info = infoFor(id);
        return switch (info.category)
            {
            case DBSchema:    new DBSchemaImpl(storeFor(id).as(SchemaStore));
            case DBCounter:   new DBCounterImpl(storeFor(id).as(CounterStore));
            case DBValue:     createValueImpl(info, storeFor(id).as(ValueStore));
            case DBMap:       createMapImpl(info, storeFor(id).as(MapStore));
            case DBList:      TODO
            case DBQueue:     TODO
            case DBProcessor: TODO
            case DBLog:       TODO
            };
        }

    private DBMapImpl createMapImpl(DBObjectInfo info, MapStore store)
        {
        assert Type keyType := info.typeParams.get("Key"),
                    keyType.is(Type<immutable Const>);
        assert Type valType := info.typeParams.get("Value"),
                    valType.is(Type<immutable Const>);

        return new DBMapImpl<keyType.DataType, valType.DataType>(store);
        }

    private DBValueImpl createValueImpl(DBObjectInfo info, ValueStore store)
        {
        assert Type valueType := info.typeParams.get("Value"),
                    valueType.is(Type<immutable Const>);

        return new DBValueImpl<valueType.DataType>(store);
        }

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    ObjectStore storeFor(String id)
        {
        return Catalog.storeFor(id);
        }


    // ----- DBObject ------------------------------------------------------------------------------

    /**
     * The shared base implementation for all of the client DBObject representations.
     */
    @Abstract
    class DBObjectImpl(ObjectStore store_)
            implements oodb.DBObject
        {
        /**
         * The corresponding store.
         */
        ObjectStore store_;

        @Override
        oodb.DBObject? dbParent.get()
            {
            return dbObjects.getOrNull(store_.info.parentId);
            }

        @Override
        String dbName.get()
            {
            return store_.info.id;
            }

        @Override
        DBCategory dbCategory.get()
            {
            return store_.info.category;
            }

        @Override
        @Lazy Map<String, oodb.DBObject> dbChildren.calc()
            {
            DBObjectInfo info     = store_.info;
            String[]     childIds = info.childIds;
            Int          size     = childIds.size;
            if (size == 0)
                {
                return Map:[];
                }

            ListMap<String, DBObject> children = new ListMap(size);
            childIds.associate(id -> {val dbo = implFor(id); return id, dbo;}, children);
            return children;
            }
        }


    // ----- DBSchema ------------------------------------------------------------------------------

    /**
     * The DBSchema DBObject implementation.
     */
    class DBSchemaImpl(SchemaStore store_)
            extends DBObjectImpl(store_)
            implements DBSchema
        {
        @Override
        @RO DBSchema!? dbParent.get()
            {
            return dbName == ""
                    ? Null
                    : super().as(DBSchema);
            }
        }


    // ----- RootSchema ----------------------------------------------------------------------------

    /**
     * The RootSchema DBObject implementation.
     */
    class RootSchemaImpl(SchemaStore store_)
            extends DBSchemaImpl(store_)
            implements RootSchema
        {
        @Override
        SystemSchema sys.get()
            {
            TODO
            }
        }


    // ----- SystemSchema --------------------------------------------------------------------------

    /**
     * The SystemSchema DBObject implementation.
     */
    class SystemSchemaImpl(SchemaStore store_)
            extends DBSchemaImpl(store_)
            implements SystemSchema
        {
        @Override
        @RO DBValue<DBInfo> info.get()
            {
            return implFor("sys/info").as(DBValue<DBInfo>);
            }

        @Override
        @RO DBMap<String, DBUser> users.get()
            {
            return implFor("sys/users").as(DBMap<String, DBUser>);
            }

        @Override
        @RO DBMap<String, Type> types.get()
            {
            return implFor("sys/types").as(DBMap<String, Type>);
            }

        @Override
        @RO DBMap<String, DBObject> objects.get()
            {
            return implFor("sys/objects").as(DBMap<String, DBObject>);
            }

        @Override
        @RO DBMap<String, DBSchema> schemas.get()
            {
            return implFor("sys/schemas").as(DBMap<String, DBSchema>);
            }

        @Override
        @RO DBMap<String, DBCounter> counters.get()
            {
            return implFor("sys/counters").as(DBMap<String, DBCounter>);
            }

        @Override
        @RO DBMap<String, DBValue> values.get()
            {
            return implFor("sys/values").as(DBMap<String, DBValue>);
            }

        @Override
        @RO DBMap<String, DBMap> maps.get()
            {
            return implFor("sys/maps").as(DBMap<String, DBMap>);
            }

        @Override
        @RO DBMap<String, DBList> lists.get()
            {
            return implFor("sys/lists").as(DBMap<String, DBList>);
            }

        @Override
        @RO DBMap<String, DBQueue> queues.get()
            {
            return implFor("sys/queues").as(DBMap<String, DBQueue>);
            }

        @Override
        @RO DBMap<String, DBProcessor> processors.get()
            {
            return implFor("sys/processors").as(DBMap<String, DBProcessor>);
            }

        @Override
        @RO DBMap<String, DBLog> logs.get()
            {
            return implFor("sys/logs").as(DBMap<String, DBLog>);
            }

        @Override
        @RO DBList<DBPending> pending.get()
            {
            return implFor("sys/pending").as(DBList<DBPending>);
            }

        @Override
        @RO DBLog<DBTransaction> transactions.get()
            {
            return implFor("sys/transactions").as(DBLog<DBTransaction>);
            }

        @Override
        @RO DBLog<String> errors.get()
            {
            return implFor("sys/errors").as(DBLog<String>);
            }
        }


    // ----- Connection ----------------------------------------------------------------------------

    /**
     * The Connection API, for providing to a database client.
     */
    class Connection(SchemaStore store_)
            extends RootSchemaImpl(store_)
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

            (Transaction + Schema) newTx = new Transaction(store_, txInfo).as(Transaction + Schema);

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
    class Transaction(SchemaStore store_, TxInfo txInfo)
            extends RootSchemaImpl(store_)
            implements oodb.Transaction<Schema>
        {
        /**
         * The map of DBObjectImpl enlisted into this transaction keyed by DBObject id.
         */
        protected Map<String, DBObjectImpl> enlisted_ = new HashMap();

        void enlist(DBObjectImpl impl)
            {
            enlisted_.put(impl.dbName, impl);
            }

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

            ObjectStore[] stores = new ObjectStore[];
            enlisted_.values.map(impl -> impl.store_, stores);

            Boolean result = Catalog.commit(stores.makeImmutable(), this.Client.id);

            enlisted_.clear();
            outer.tx = Null; // pending == False

            return result;
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

            ObjectStore[] stores = new ObjectStore[];
            enlisted_.values.map(impl -> impl.store_, stores);

            Catalog.rollback(stores.makeImmutable(), this.Client.id);

            enlisted_.clear();
            outer.tx  = Null; // pending == False
            }
        }


    // ----- TxContext -----------------------------------------------------------------------------

    /**
     * The TxContext simplifies transaction management on an operation-by-operation basis.
     */
    protected class TxContext
            implements Closeable
        {
        (Transaction + Schema)? tx;
        Boolean                 autocommit;

        void init(Transaction + Schema tx, Boolean autocommit)
            {
            assert this.tx == Null;
            this.tx = tx;
            this.autocommit = autocommit;
            }

        @Override
        void close(Exception? e = Null)
            {
            assert Transaction tx ?= this.tx;
            if (autocommit)
                {
                assert tx.commit(); // see jsondb/Client.x
                }
            this.tx = Null;
            }
        }


    // ----- DBValue ---------------------------------------------------------------------------

    /**
     * The DBValue DBObject implementation.
     */
    class DBValueImpl<Value extends immutable Const>(ValueStore<Value> store_)
            extends DBObjectImpl(store_)
            implements DBValue<Value>
        {
        @Override
        ValueStore<Value> store_.get()
            {
            return super().as(ValueStore<Value>);
            }

        @Override
        Value get()
            {
            return transactional && this.Client.joinTransaction(this)
                ? store_.getValueAt(this.Client.id)
                : store_.getValue();
            }

        @Override
        void set(Value value)
            {
            if (transactional && this.Client.joinTransaction(this))
                {
                store_.setValueAt(this.Client.id, value);
                }
            else
                {
                store_.setValue(value);
                }
            }
        }


    // ----- DBCounter -----------------------------------------------------------------------------

    /**
     * The DBCounter implementation.
     */
    class DBCounterImpl(CounterStore store_)
            extends DBValueImpl<Int>(store_)
            implements DBCounter
        {
        @Override
        CounterStore store_.get()
            {
            return super().as(CounterStore);
            }

        @Override
        Boolean transactional.get()
            {
            return store_.info.transactional;
            }

        @Override
        void adjustBy(Int value)
            {
            if (transactional && this.Client.joinTransaction(this))
                {
                store_.adjustByAt(this.Client.id, value);
                }
            else
                {
                store_.adjustBy(value);
                }
            }
        }


    // ----- DBMap ---------------------------------------------------------------------------------

    /**
     * The DBMap implementation.
     */
    class DBMapImpl<Key extends immutable Const, Value extends immutable Const>
        (MapStore<Key, Value> store_)
            extends DBObjectImpl(store_)
            implements DBMap<Key, Value>
            incorporates KeySetBasedMap<Key, Value>
        {
        @Override
        MapStore<Key, Value> store_.get()
            {
            return super().as(MapStore<Key, Value>);
            }

        @Override
        @RO Int size.get()
            {
            return this.Client.joinTransaction(this)
                ? store_.sizeAt(this.Client.id)
                : store_.size;
            }

        @Override
        @RO Boolean empty.get()
            {
            return this.Client.joinTransaction(this)
                ? store_.emptyAt(this.Client.id)
                : store_.empty;
            }

        @Override
        Boolean contains(Key key)
            {
            return this.Client.joinTransaction(this)
                ? store_.containsAt(this.Client.id, key)
                : store_.contains(key);
            }

        @Override
        conditional Value get(Key key)
            {
            return this.Client.joinTransaction(this)
                ? store_.getAt(this.Client.id, key)
                : store_.get(key);
            }

        @Override
        Set<Key> keys.get()
            {
            return this.Client.joinTransaction(this)
                ? store_.keysAt(this.Client.id)
                : store_.keysSnapshot();
            }

        @Override
        DBMapImpl put(Key key, Value value)
            {
            if (this.Client.joinTransaction(this))
                {
                store_.putAt(this.Client.id, key, value);
                }
            else
                {
                store_.put(key, value);
                }
            return this;
            }

        @Override
        DBMapImpl remove(Key key)
            {
            if (this.Client.joinTransaction(this))
                {
                store_.removeAt(this.Client.id, key);
                }
            else
                {
                store_.remove(key);
                }
            return this;
            }
        }
    }