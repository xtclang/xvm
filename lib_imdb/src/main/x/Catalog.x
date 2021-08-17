import oodb.DBCounter;
import oodb.DBFunction;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBQueue;
import oodb.DBSchema;
import oodb.DBUser;
import oodb.DBValue;
import oodb.Permission;
import oodb.RootSchema;

import oodb.model.DBUser as DBUserImpl;


/**
 * Catalog for the in-memory database (unlike the jsonDB) serves as the object store itself.
 */
static service Catalog
    {
    /**
     * Catalog initialization.
     */
    void initialize(CatalogMetadata metadata)
        {
        assert this.metadata == Null; // once and only once

        this.metadata = metadata;
        }


    // ----- built-in system schema ----------------------------------------------------------------

    static Map<String, DBObjectInfo> SystemInfos = Map:
        [
        ""    = new DBObjectInfo("",    DBSchema, DBSchema),
        "sys" = new DBObjectInfo("sys", DBSchema, DBSchema, "",
            [
            "sys/info",
            "sys/users",
            "sys/types",
            "sys/objects",
            "sys/schemas",
            "sys/maps",
            "sys/queues",
            "sys/lists",
            "sys/logs",
            "sys/counters",
            "sys/values",
            "sys/functions",
            "sys/pending",
            "sys/transactions",
            "sys/errors",
            ]),

        "sys/info"         = new DBObjectInfo("sys/info",         DBValue, DBValue<DBInfo>),
        "sys/users"        = new DBObjectInfo("sys/users",        DBMap,   DBMap<String, DBUser>),
        "sys/types"        = new DBObjectInfo("sys/types",        DBMap,   DBMap<String, Type>),
        "sys/objects"      = new DBObjectInfo("sys/objects",      DBMap,   DBMap<String, DBObject>),
        "sys/schemas"      = new DBObjectInfo("sys/schemas",      DBMap,   DBMap<String, DBSchema>),
        "sys/maps"         = new DBObjectInfo("sys/maps",         DBMap,   DBMap<String, DBMap>),
        "sys/queues"       = new DBObjectInfo("sys/queues",       DBMap,   DBMap<String, DBQueue>),
        "sys/lists"        = new DBObjectInfo("sys/lists",        DBMap,   DBMap<String, DBList>),
        "sys/logs"         = new DBObjectInfo("sys/logs",         DBMap,   DBMap<String, DBLog>),
        "sys/counters"     = new DBObjectInfo("sys/counters",     DBMap,   DBMap<String, DBCounter>),
        "sys/values"       = new DBObjectInfo("sys/values",       DBMap,   DBMap<String, DBValue>),
        "sys/functions"    = new DBObjectInfo("sys/functions",    DBMap,   DBMap<String, DBFunction>),
        "sys/pending"      = new DBObjectInfo("sys/pending",      DBList,  DBList<>),
        "sys/transactions" = new DBObjectInfo("sys/transactions", DBLog,   DBLog<>),
        "sys/errors"       = new DBObjectInfo("sys/errors",       DBLog,   DBLog<>),
        ];

    /**
     * Default "system" user.
     */
    static protected DBUserImpl DefaultUser = new DBUserImpl(0, "sys",
            permissions = [new Permission(AllTargets, AllActions)]);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The catalog metadata for this catalog. This information must be provided once and only once
     * via the 'initialize' API.
     */
    public/private CatalogMetadata? metadata;

    /**
     * The map of `DBObjectStore`s in the `Catalog` keyed by the 'id'.
     */
    protected/private Map<String, DBObjectStore> stores = new HashMap();

    /**
     * An error and message log for the database.
     */
    Appender<String> log = new String[]; // TODO

    /**
     * The existing client representations for this `Catalog` object. Each client may have a single
     * Connection representation, and each Connection may have a single Transaction representation.
     */
    protected/private Map<Int, Client> clients = new HashMap();

    /**
     * The number of clients created by this Catalog. Used as the generator for client IDs.
     */
    protected Int clientCounter = 0;


    // ----- visibility ----------------------------------------------------------------------------

    @Override
    String toString()
        {
        return $"Catalog for {metadata?.schemaModule : "N/A"}";
        }


    // ----- support ----------------------------------------------------------------------------

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the object id
     *
     * @return DBObjectInfo for the specified id
     * @throws IllegalArgument if there is no DBObjectInfo for the specified id
     */
    DBObjectInfo infoFor(String id)
        {
        DBObjectInfo info;
        if (info := metadata?.dbObjectInfos.get(id))
            {
            return info;
            }

        if (info := SystemInfos.get(id))
            {
            return info;
            }

        throw new IllegalArgument($"no info for \"{id}\"");
        }

    /**
     * Obtain the DBObjectStore for the specified id.
     *
     * @param id  the object id
     *
     * @return DBObject for the specified id
     * @throws IllegalArgument if there is no DBObjectInfo for the specified id
     */
    DBObjectStore storeFor(String id)
        {
        DBObjectStore store;
        if (store := stores.get(id))
            {
            return store;
            }

        store = createStore(infoFor(id));

        stores.put(id, store);
        return store;
        }

    /**
     * Create an DBObjectStore for the specified database object id.
     *
     * @param id  the object id
     *
     * @return the new DBObjectStore
     */
    DBObjectStore createStore(DBObjectInfo info)
        {
        return switch (info.category)
            {
            case DBSchema:   new SchemaStore(info, log);
            case DBMap:      createMapStore(info, log);
            case DBList:     TODO
            case DBQueue:    TODO
            case DBLog:      TODO
            case DBCounter:  new CounterStore(info, log);
            case DBValue:    createValueStore(info, log);
            case DBFunction: TODO
            default:         assert;
            };
        }

    // TODO GG: if inlined, doesn't compile (registers mismatch)
    DBObjectStore createMapStore(DBObjectInfo info, Appender<String> log)
        {
        Type<DBMap> typeDBMap = info.type.as(Type<DBMap>);
        assert Type keyType := typeDBMap.resolveFormalType("Key")  , keyType.is(Type<immutable Const>);
        assert Type valType := typeDBMap.resolveFormalType("Value"), valType.is(Type<immutable Const>);
        return new MapStore<keyType.DataType, valType.DataType>(info, log);
        }

    DBObjectStore createValueStore(DBObjectInfo info, Appender<String> log)
        {
        Type<DBValue> typeDBValue = info.type.as(Type<DBValue>);
        assert Type valueType := typeDBValue.resolveFormalType("Value"), valueType.is(Type<immutable Const>);

        assert Class  valueClass   := valueType.fromClass(),
               Object defaultValue := valueClass.defaultValue();

        return new ValueStore<valueType.DataType>(info, log, defaultValue.as(valueType.DataType));
        }

    /**
     * In-memory schema store has no additional state.
     */
    class SchemaStore(DBObjectInfo info, Appender<String> log)
            extends DBObjectStore(info, log)
        {
        }

    /**
     * MapStore as a virtual child.
     */
    class MapStore<Key extends immutable Const, Value extends immutable Const>
        (DBObjectInfo info, Appender<String> log)
            extends storage.MapStore<Key, Value>(info, log)
        {
        }

    /**
     * CounterStore as a virtual child.
     */
    class CounterStore(DBObjectInfo info, Appender<String> log)
            extends storage.CounterStore(info, log)
        {
        }

    /**
     * CounterStore as a virtual child.
     */
    class ValueStore<Value extends immutable Const>(DBObjectInfo info, Appender<String> log, Value defaultValue)
            extends storage.ValueStore<Value>(info, log, defaultValue)
        {
        }


    // ----- Client management ---------------------------------------------------------------------

    /**
     * Create a `Client` that will access the database represented by this `Catalog`  on behalf of
     * the specified user. This method allows a custom (e.g. code-gen) `Client` implementation to
     * be substituted for the default, which allows custom schemas and other custom functionality to
     * be provided in a type-safe manner.
     *
     * @param dbUser    the user that the `Client` will represent
     * @param readOnly  (optional) pass True to indicate that client is not permitted to modify
     *                  any data
     *
     * @return a new `Client` instance
     */
    Client createClient(DBUser dbUser, Boolean readOnly = False)
        {
        return metadata?.createClient(genClientId(), dbUser, readOnly, unregisterClient) : assert;
        }

    /**
     * Generate a unique client id. (Unique for the lifetime of this Catalog.)
     *
     * @return a new client id
     */
    protected Int genClientId()
        {
        return ++clientCounter;
        }

    /**
     * Register a Client instance.
     *
     * @param client  the Client object to register
     */
    protected void registerClient(Client client)
        {
        assert clients.putIfAbsent(client.id, client);
        }

    /**
     * Unregister a Client instance.
     *
     * @param client  the Client object to unregister
     */
    protected void unregisterClient(Client client)
        {
        assert clients.remove(client.id, client);
        }


    // ----- Transaction support -------------------------------------------------------------------

    Boolean commit(DBObjectStore[] stores, Int clientId)
        {
// TODO GG
//        for (DBObjectStore store : stores)
//            {
//            prepare(clientId);
//            }

        for (DBObjectStore store : stores)
            {
            store.apply(clientId);
            }
        return True;
        }

    void rollback(DBObjectStore[] stores, Int clientId)
        {
        for (DBObjectStore store : stores)
            {
            store.discard(clientId);
            }
        }
    }
