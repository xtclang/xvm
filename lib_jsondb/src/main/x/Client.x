import ecstasy.reflect.Annotation;

import json.Lexer.Token;

import oodb.DBMap;
import oodb.DBObject;
import oodb.DBSchema;
import oodb.DBTransaction;
import oodb.DBUser;
import oodb.DBValue;
import oodb.NoTx;
import oodb.RootSchema;

import model.DBObjectInfo;

import storage.MapStore;
import storage.ObjectStore;
import storage.ValueStore;


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
    construct(Catalog<Schema> catalog, Int id, DBUser dbUser, function void(Client)? notifyOnClose = Null)
        {
        assert Schema == RootSchema || catalog.schemaMixin != Null;

        this.id            = id;
        this.dbUser        = dbUser;
        this.catalog       = catalog;
        this.schemaMixin   = catalog.schemaMixin;
        this.notifyOnClose = notifyOnClose;
        }
    finally
        {
        if (schemaMixin == Null)
            {
            // TODO GG
            // conn = new Connection<Schema>().as(Connection<Schema> + Schema);
            conn = Null;
            }
        else
            {
            // TODO GG
            // Class<Connection + Schema> clz = Connection<Schema>.annotate(new Annotation(schemaMixin?)).as(Class<Connection + Schema>) : assert;
            // REVIEW or is this necessary?  Class<...> clz = Client<Schema>.Connection.annotate(new Annotation(schemaMixin)).as(Client<Schema>.Connection + Schema);
            // conn = clz.instantiate(clz.allocate(), this);
            conn = Null;
            }
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
     * The mixin for the root schema implementation.
     */
    protected Class<Schema>? schemaMixin;

    /**
     * The lazily created DBObjects within the schema. REVIEW
     */
    protected DBObject?[] dbObjects;

    /**
     * The function to use to notify that the connection has closed.
     */
    protected function void(Client)? notifyOnClose;

    /**
     * An interface representing a Client context, which is either a Connection or a Transaction.
     */
    interface Context {}


    // ----- support ----------------------------------------------------------------------------

    Boolean check()
        {
        // TODO
        return True;
        }

    DBObjectInfo infoFor(Int id)
        {
        TODO
        }

    DBObjectImpl implFor(Int id)
        {
        TODO
        }


    // ----- Connection ----------------------------------------------------------------------------

    /**
     * The Connection API, for providing to a database client.
     */
    class Connection
            implements Context
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
            // TODO GG why is cast required?
            return outer.tx?.as(Transaction + Schema) : Null;
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
    class Transaction(Int baseId_)
            implements Context
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
// TODO GG why is cast required?
//            return outer.conn ?: assert;
            return outer.conn?.as(Connection + Schema) : assert;
            }

        @Override
        @RO Boolean pending.get()
            {
            return &this == outer.&tx;
            }

        @Override
        Boolean commit()
            {
// TODO GG why is cast required?
//            Transaction? that = outer.tx;
            val that = outer.tx;
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
// TODO GG  Transaction? that = outer.tx;
            val that = outer.tx;
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
                    // TODO GG? - childIds.associate(i -> (infoFor(i).name, infoFor(i)))
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
