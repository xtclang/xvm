import oodb.DBCounter;
import oodb.DBInfo;
import oodb.DBList;
import oodb.DBLog;
import oodb.DBMap;
import oodb.DBObject;
import oodb.DBProcessor;
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
        ""    = new DBObjectInfo("",    DBSchema),
        "sys" = new DBObjectInfo("sys", DBSchema, "",
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

        "sys/info"         = new DBObjectInfo("sys/info",         DBValue, typeParams=Map:["Value"=DBInfo]),
        "sys/users"        = new DBObjectInfo("sys/users",        DBMap,   typeParams=Map:["Key"=String, "Value"=DBUser]),
        "sys/types"        = new DBObjectInfo("sys/types",        DBMap,   typeParams=Map:["Key"=String, "Value"=Type]),
        "sys/objects"      = new DBObjectInfo("sys/objects",      DBMap,   typeParams=Map:["Key"=String, "Value"=DBObject]),
        "sys/schemas"      = new DBObjectInfo("sys/schemas",      DBMap,   typeParams=Map:["Key"=String, "Value"=DBSchema]),
        "sys/counters"     = new DBObjectInfo("sys/counters",     DBMap,   typeParams=Map:["Key"=String, "Value"=DBCounter]),
        "sys/values"       = new DBObjectInfo("sys/values",       DBMap,   typeParams=Map:["Key"=String, "Value"=DBValue]),
        "sys/maps"         = new DBObjectInfo("sys/maps",         DBMap,   typeParams=Map:["Key"=String, "Value"=DBMap]),
        "sys/lists"        = new DBObjectInfo("sys/lists",        DBMap,   typeParams=Map:["Key"=String, "Value"=DBList]),
        "sys/processors"   = new DBObjectInfo("sys/processors",   DBMap,   typeParams=Map:["Key"=String, "Value"=DBProcessor]),
        "sys/queues"       = new DBObjectInfo("sys/queues",       DBMap,   typeParams=Map:["Key"=String, "Value"=DBQueue]),
        "sys/logs"         = new DBObjectInfo("sys/logs",         DBMap,   typeParams=Map:["Key"=String, "Value"=DBLog]),
        "sys/pending"      = new DBObjectInfo("sys/pending",      DBList,  typeParams=Map:[]),
        "sys/transactions" = new DBObjectInfo("sys/transactions", DBLog,   typeParams=Map:[]),
        "sys/errors"       = new DBObjectInfo("sys/errors",       DBLog,   typeParams=Map:[]),
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
     * The map of `ObjectStore`s in the `Catalog` keyed by the 'id'.
     */
    protected/private Map<String, ObjectStore> stores = new HashMap();

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
     * Obtain the ObjectStore for the specified id.
     *
     * @param id  the object id
     *
     * @return DBObject for the specified id
     * @throws IllegalArgument if there is no DBObjectInfo for the specified id
     */
    ObjectStore storeFor(String id)
        {
        ObjectStore store;
        if (store := stores.get(id))
            {
            return store;
            }

        store = createStore(infoFor(id));

        stores.put(id, store);
        return store;
        }

    /**
     * Create an ObjectStore for the specified database object id.
     *
     * @param id  the object id
     *
     * @return the new ObjectStore
     */
    ObjectStore createStore(DBObjectInfo info)
        {
        return switch (info.category)
            {
            case DBSchema:    new SchemaStore(info, log);
            case DBCounter:   new CounterStore(info, log);
            case DBValue:     createValueStore(info, log);
            case DBMap:       createMapStore(info, log);
            case DBList:      TODO
            case DBQueue:     TODO
            case DBProcessor: TODO
            case DBLog:       TODO
            };
        }

    // TODO GG: if inlined, doesn't compile (registers mismatch)
    ObjectStore createMapStore(DBObjectInfo info, Appender<String> log)
        {
        assert Type keyType := info.typeParams.get("Key"),
                    keyType.is(Type<immutable Const>);
        assert Type valType := info.typeParams.get("Value"),
                    valType.is(Type<immutable Const>);

        return new MapStore<keyType.DataType, valType.DataType>(info, log);
        }

    ObjectStore createValueStore(DBObjectInfo info, Appender<String> log)
        {
        assert Type valueType := info.typeParams.get("Value"),
                    valueType.is(Type<immutable Const>);

        assert Object initial := info.options.get("initial");
        return new ValueStore<valueType.DataType>(info, log, initial.as(valueType.DataType));
        }

    /**
     * In-memory schema store has no additional state.
     */
    class SchemaStore(DBObjectInfo info, Appender<String> log)
            extends ObjectStore(info, log)
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

    Boolean commit(ObjectStore[] stores, Int clientId)
        {
// TODO GG
//        for (ObjectStore store : stores)
//            {
//            prepare(clientId);
//            }

        for (ObjectStore store : stores)
            {
            store.apply(clientId);
            }
        return True;
        }

    void rollback(ObjectStore[] stores, Int clientId)
        {
        for (ObjectStore store : stores)
            {
            store.discard(clientId);
            }
        }
    }
