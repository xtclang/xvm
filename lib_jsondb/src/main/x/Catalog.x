import model.DBObjectInfo;
import model.Lock;
import model.SysInfo;

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
import oodb.Permission;
import oodb.RootSchema;

import oodb.model.DBUser as DBUserImpl;

import storage.ObjectStore;


/**
 * Metadata catalog for a database. A `Catalog` acts as the "gateway" to a JSON database, allowing a
 * database to be created, opened, examined, recovered, upgraded, and/or deleted.
 *
 * A `Catalog` is instantiated as a combination of a database module which provides the model (the
 * Ecstasy representation) for the database, and a filing system directory providing the storage for
 * the database's data. The `Catalog` does not require the module to be provided; it can be omitted
 * so that a database on disk can be examined and (to a limited extent) manipulated/repaired without
 * explicit knowledge of its Ecstasy representation.
 *
 * The `Catalog` has a weak notion of mutual exclusion, designed to avoid database corruption that
 * could occur if two instances attempted to open the same database in a read/write mode:
 *
 * * A file [statusFile] contains a status that indicates whether a Catalog instance may already be
 *   [Configuring](Status.Configuring), [Running](Status.Running), or
 *   [Recovering](Status.Recovering)
 * * The absence of a status file, or a status file with the [Closed](Status.Closed) status
 *   indicates that the Catalog is not in use.
 * * If a crash occurs while the Catalog is in use, the status file may still indicate that the
 *   Catalog is in use after the crash; this requires the Catalog to be recovered using the
 *   [recover] method.
 * * Transitions of the status file itself are guarded by using a second lock file (relying on the
 *   atomicity of file creation), whose temporary existence indicates that a status file transition
 *   is in progress.
 *
 * TODO version - should only be able to open the catalog with the correct TypeSystem version
 */
service Catalog<Schema extends RootSchema>
        implements Closeable
    {
    typedef (Client.Connection + Schema) Connection;

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Open the catalog for the specified directory.
     *
     * @param dir          the directory that contains (or may contain) the catalog
     * @param metadata  (optional) the `CatalogMetadata` for this `Catalog`; if the metadata is not
     *                  provided, then the `Catalog` can only operate on the database as a raw JSON
     *                  data store
     * @param readOnly  (optional) pass `True` to access the catalog in a read-only manner
     */
    construct(Directory dir, CatalogMetadata<Schema>? metadata = Null, Boolean readOnly = False)
        {
        assert:arg dir.exists && dir.readable && (readOnly || dir.writable);
        assert metadata != Null || Schema == RootSchema;

        @Inject Clock clock;

        this.timestamp   = clock.now;
        this.dir         = dir;
        this.metadata    = metadata;
        this.version     = metadata?.version : Null;
        this.readOnly    = readOnly;
        this.status      = Closed;
        }


    // ----- built-in system schema ----------------------------------------------------------------

    /**
     * An enumeration of built-in (system) database objects.
     */
    enum BuiltIn<ObjectType>
        {
        Root<DBSchema>,
        Sys<DBSchema>,
        Info<DBValue<DBInfo>>,
        Users<DBMap<String, DBUser>>,
        Types<DBMap<String, Type>>,
        Objects<DBMap<String, DBObject>>,
        Schemas<DBMap<String, DBSchema>>,
        Maps<DBMap<String, DBMap>>,
        Queues<DBMap<String, DBQueue>>,
        Lists<DBMap<String, DBList>>,
        Logs<DBMap<String, DBLog>>,
        Counters<DBMap<String, DBCounter>>,
        Values<DBMap<String, DBValue>>,
        Functions<DBMap<String, DBFunction>>,
        Pending<DBList<DBInvoke>>,
        Transactions<DBLog<DBTransaction>>,
        Errors<DBLog<String>>,
        ;

        /**
         * The internal id of the built-in database object. The root schema is 0, while the rest of
         * the built-in database objects use negative ids.
         */
        Int id.get()
            {
            return -ordinal;
            }

        /**
         * The DBObjectInfo for the built-in database object.
         */
        DBObjectInfo info.get()
            {
            return SystemInfos[ordinal];
            }

        /**
         * Obtain the BuiltIn enum value for the specified built-in database object id.
         *
         * @param the built-in id
         *
         * @return the BuiltIn value representing the built-in database object
         */
        static BuiltIn byId(Int id)
            {
            assert id <= 0 && id + BuiltIn.count > 0;
            return BuiltIn.values[0-id];
            }
        }

    static DBObjectInfo[] SystemInfos =
        [
        new DBObjectInfo("",    "",    DBSchema, BuiltIn.Root.id, BuiltIn.Root.id,
            [
            BuiltIn.Sys.id,
            ]),
        new DBObjectInfo("sys", "sys", DBSchema, BuiltIn.Sys.id,  BuiltIn.Root.id,
            [
            BuiltIn.Info.id,
            BuiltIn.Users.id,
            BuiltIn.Types.id,
            BuiltIn.Objects.id,
            BuiltIn.Schemas.id,
            BuiltIn.Maps.id,
            BuiltIn.Queues.id,
            BuiltIn.Lists.id,
            BuiltIn.Logs.id,
            BuiltIn.Counters.id,
            BuiltIn.Values.id,
            BuiltIn.Functions.id,
            BuiltIn.Pending.id,
            BuiltIn.Transactions.id,
            BuiltIn.Errors.id,
            ]),
// TODO GG: the use of ".PublicType" in the following lines should not be necessary
        new DBObjectInfo("info",         "sys/info",         DBValue, BuiltIn.Info.id,         BuiltIn.Sys.id, typeParams=Map:["Value"=DBInfo.PublicType]),
        new DBObjectInfo("users",        "sys/users",        DBMap,   BuiltIn.Users.id,        BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBUser.PublicType]),
        new DBObjectInfo("types",        "sys/types",        DBMap,   BuiltIn.Types.id,        BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=Type.PublicType]),
        new DBObjectInfo("objects",      "sys/objects",      DBMap,   BuiltIn.Objects.id,      BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBObject.PublicType]),
        new DBObjectInfo("schemas",      "sys/schemas",      DBMap,   BuiltIn.Schemas.id,      BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBSchema.PublicType]),
        new DBObjectInfo("maps",         "sys/maps",         DBMap,   BuiltIn.Maps.id,         BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBMap.PublicType]),
        new DBObjectInfo("queues",       "sys/queues",       DBMap,   BuiltIn.Queues.id,       BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBQueue.PublicType]),
        new DBObjectInfo("lists",        "sys/lists",        DBMap,   BuiltIn.Lists.id,        BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBList.PublicType]),
        new DBObjectInfo("logs",         "sys/logs",         DBMap,   BuiltIn.Logs.id,         BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBLog.PublicType]),
        new DBObjectInfo("counters",     "sys/counters",     DBMap,   BuiltIn.Counters.id,     BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBCounter.PublicType]),
        new DBObjectInfo("values",       "sys/values",       DBMap,   BuiltIn.Values.id,       BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBValue.PublicType]),
        new DBObjectInfo("functions",    "sys/functions",    DBMap,   BuiltIn.Functions.id,    BuiltIn.Sys.id, typeParams=Map:["Key"=String.PublicType, "Value"=DBFunction.PublicType]),
        new DBObjectInfo("pending",      "sys/pending",      DBList,  BuiltIn.Pending.id,      BuiltIn.Sys.id, typeParams=Map:["Element"=DBInvoke.PublicType]),
        new DBObjectInfo("transactions", "sys/transactions", DBLog,   BuiltIn.Transactions.id, BuiltIn.Sys.id, typeParams=Map:["Element"=DBTransaction.PublicType]),
        new DBObjectInfo("errors",       "sys/errors",       DBLog,   BuiltIn.Errors.id,       BuiltIn.Sys.id, typeParams=Map:["Element"=String.PublicType]),
        ];

    /**
     * Default "system" user.
     */
    static protected DBUserImpl DefaultUser = new DBUserImpl(0, "sys",
            permissions = [new Permission(AllTargets, AllActions)]);


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The timestamp from when this Catalog was created; used as an assumed-unique identifier.
     */
    public/private DateTime timestamp;

    /**
     * The directory used to store the contents of the database
     */
    public/private Directory dir;

    /**
     * The optional catalog metadata for this catalog. This information is typically created by a
     * code generation process that takes as its input an application's "@Database" module, and
     * emits as output a new module that provides a custom binding of the application's "@Database"
     * module to the `jsondb` implementation of the `oodb` database API.
     */
    public/private CatalogMetadata<Schema>? metadata;

    /**
     * The JSON Schema to use.
     */
    @Lazy json.Schema jsonSchema.calc()
        {
        return metadata?.jsonSchema : json.Schema.DEFAULT;
        }

    /**
     * True iff the database was opened in read-only mode.
     */
    public/private Boolean readOnly;

    /**
     * The version of the database represented by this `Catalog` object. The version may not be
     * available before the database is opened.
     */
    public/private Version? version;

    /**
     * The status of this `Catalog` object.
     */
    @Atomic public/private Status status;

    /**
     * The ObjectStore for each DBObject in the `Catalog`. These provide the I/O for the database.
     *
     * This data is available from the catalog through various methods; the array itself is not
     * exposed in order to avoid any concerns related to transmitting it through service boundaries.
     */
    protected/private ObjectStore?[] appStores = new ObjectStore[];

    /**
     * The ObjectStore for each DBObject in the system schema. These are read-only stores that
     * provide a live view of the database metadata and status.
     */
    protected/private ObjectStore?[] sysStores = new ObjectStore[];

    /**
     * The transaction manager for this `Catalog` object. The transaction manager provides a
     * sequential ordered (non-concurrent) application of potentially concurrent transactions.
     */
    @Lazy public/private TxManager txManager.calc()
        {
        return new TxManager(this);
        }

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
        return $"{this:class.name}:\{dir={dir}, version={version}, status={status}, readOnly={readOnly}, unique-id={timestamp}}";
        }


    // ----- support ----------------------------------------------------------------------------

    /**
     * Obtain the DBObjectInfo for the specified id.
     *
     * @param id  the internal object id
     *
     * @return the DBObjectInfo for the specified id
     */
    DBObjectInfo infoFor(Int id)
        {
        assert id <= 0;
        return BuiltIn.byId(id).info;
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
        ObjectStore?[] stores = appStores;
        Int            index  = id;
        if (id < 0)
            {
            stores = sysStores;
            index  = BuiltIn.byId(id).ordinal;
            }

        Int size = stores.size;
        if (index < size)
            {
            return stores[index]?;
            }

        ObjectStore store = createStore(id);

        // save off the ObjectStore (lazy cache)
        if (index > stores.size)
            {
            stores.fill(Null, stores.size..index);
            }
        stores[index] = store;

        return store;
        }

    /**
     * Create an ObjectStore for the specified internal database object id.
     *
     * @param id  the internal object id
     *
     * @return the new ObjectStore
     */
    ObjectStore createStore(Int id)
        {
        // TODO

        DBObjectInfo info  = infoFor(id);
TODO
//        return switch (BuiltIn.byId(id))
//            {
//            case Root:         assert;
//            case Sys:          assert;
//            case Info:         assert;
//            case Users:        TODO new DBMap<String, DBUser>();
//            case Types:        TODO new DBMap<String, Type>();
//            case Objects:      TODO new DBMap<String, DBObject>();
//            case Schemas:      assert;
//            case Maps:         assert;
//            case Queues:       assert;
//            case Lists:        assert;
//            case Logs:         assert;
//            case Counters:     assert;
//            case Values:       assert;
//            case Functions:    assert;
//            case Pending:      TODO new DBList<DBInvoke>();
//            case Transactions: TODO new DBLog<DBTransaction>();
//            case Errors:       TODO new DBLog<String>();
//            default:           assert;
//            };
        }


    // ----- status management ---------------------------------------------------------------------

    /**
     * The file used to store the "in-use" status for the database.
     */
    @Lazy File statusFile.calc()
        {
        return dir.fileFor("sys.json");
        }

    /**
     * The status of this `Catalog`.
     *
     * * `Closed` - This `Catalog` object has not yet been opened, or it has been shut down.
     * * `Configuring` - This `Catalog` object has the database open for schema definition and
     *   modification, or other maintenance work.
     * * `Running` - This `Catalog` object has the database open for data access.
     * * `Recovering` - This `Catalog` object has been instructed to recover the database.
     */
    enum Status {Closed, Recovering, Configuring, Running}

    /**
     * For an empty `Catalog` that is `Closed`, initialize the directory and file structures so that
     * a catalog exists in the previously specified directory. After creation, the `Catalog` will be
     * in the `Configuring` status, allowing the caller to populate the database schema.
     *
     * @param name  the name of the database to create
     *
     * @throws IllegalState  if the Catalog is not `Empty`, or is read-only
     */
    void create(String name)
        {
        transition(Closed, Configuring, snapshot -> snapshot.empty);

        // TODO maybe return a config API?
        }

    /**
     * For an existent database, if this `Catalog` is `Closed`, `Recovering`, or `Running`, then
     * transition to the `Configuring` state, allowing modifications to be made to the database
     * structure.
     *
     * @throws IllegalState  if the Catalog is not `Closed` or `Running`, or is read-only
     */
    void edit()
        {
        transition([Closed, Recovering, Running], Configuring, snapshot -> !snapshot.empty && !snapshot.lockedOut);

        // TODO maybe return a config API?
        }

    /**
     * For an existent database, if this `Catalog` is locked-out, then assume that the previous
     * owner terminated, take ownership of the database and verify its integrity.
     *
     * @throws IllegalState  if the Catalog is not locked-out or `Closed`
     */
    void recover()
        {
        transition(Closed, Recovering, snapshot -> !snapshot.empty || sysDir.exists, ignoreLock = True);

        // TODO
        }

    /**
     * For an existent database, if this `Catalog` is `Closed`, `Recovering`, or `Configuring`, then
     * transition to the `Running` state, allowing access and modification of the database contents.
     *
     * @throws IllegalState  if the Catalog is not `Closed`, `Recovering`, or `Configuring`
     */
    void open()
        {
        transition([Closed, Recovering, Configuring], Running,
                snapshot -> snapshot.owned || snapshot.unowned,
                allowReadOnly = True);

        // TODO
        }

    /**
     * Close this `Catalog`.
     */
    @Override
    void close(Exception? cause = Null)
        {
        switch (status)
            {
            case Configuring:
            case Recovering:
                transition(status, Closed, snapshot -> snapshot.owned);
                break;

            case Running:
            case Closed:
                transition(status, Closed, snapshot -> snapshot.owned, allowReadOnly = True);
                break;

            default:
                assert;
            }
        }

    /**
     * For a `Catalog` that is `Configuring` or `Closed`, remove the entirety of the database. When
     * complete, the status will be `Closed`.
     *
     * @throws IllegalState  if the Catalog is not `Configuring` or `Closed`, or is read-only
     */
    void delete()
        {
        transition([Closed, Configuring], Configuring, snapshot -> snapshot.owned || snapshot.unowned);

        for (Directory subdir : dir.dirs())
            {
            subdir.deleteRecursively();
            }

        for (File file : dir.files())
            {
            file.delete();
            }

        transition(status, Closed, snapshot -> snapshot.empty);
        }

    /**
     * Validate that the current status matches the required status, optionally verify that the
     * Catalog is not read-only, and then with a lock in place, verify that the disk image also
     * matches that assumption. While holding that lock, optionally perform an operation, and then
     * update the status to the specified ,  (and the cor
     *
     * @param requiredStatus  one or more valid starting `Status` values
     * @param requiresWrite   `True` iff the Catalog is not allowed to be read-only
     * @param targetStatus    the ending `Status` to transition to
     * @param performAction   a function to execute while the lock is held
     *
     * @return True if the status has been changed
     */
    protected void transition(Status | Status[]         requiredStatus,
                              Status                    targetStatus,
                              function Boolean(Glance)? canTransition = Null,
                              Boolean                   allowReadOnly = False,
                              Boolean                   ignoreLock    = False)
        {
        Status oldStatus = status;
        if (requiredStatus.is(Status))
            {
            assert oldStatus == requiredStatus;
            }
        else
            {
            assert requiredStatus.contains(oldStatus);
            }

        if (readOnly)
            {
            assert allowReadOnly;
            status = targetStatus;
            }
        else
            {
            using (val lock = lock(ignoreLock))
                {
                // get a glance at the current status on disk, and verify that the requested
                // transition is legal
                Glance glance = glance();
                if (!canTransition?(glance))
                    {
                    throw new IllegalState($"Unable to transition {dir.path} from {oldStatus} to {targetStatus}");
                    }

                // store the updated status
                status = targetStatus;

                // store the updated status (unless we're closing an empty database, in which case,
                // nothing gets stored)
                if (!(targetStatus == Closed && glance.empty))
                    {
                    statusFile.contents = toBytes(new SysInfo(this));
                    }
                }
            }
        }


    // ----- directory Glance ----------------------------------------------------------------------

    /**
     * A `Glance` is a snapshot view of the database status on disk, from the point of view of the
     * `Catalog` that makes the "glancing" observation of the directory containing the database.
     */
    const Glance(SysInfo? info, Lock? lock, Exception? error)
        {
        /*
         * True iff at the moment of the snapshot, the observing `Catalog` detected that the
         * directory did not appear to contain a configured database.
         */
        Boolean empty.get()
            {
            return error == Null && info == Null;
            }

        /**
         * True iff at the moment of the snapshot, the observing `Catalog` detected that the
         * directory was not owned.
         */
        Boolean unowned.get()
            {
            return error == Null && (info?.status == Closed : True);
            }

        /**
         * True iff at the moment of the snapshot, the observing `Catalog` detected that it (and
         * not some other `Catalog` instance) was the owner of the directory.
         */
        Boolean owned.get()
            {
            return error == Null && info?.status != Closed && info?.stampedBy == this.Catalog.timestamp : False;
            }

        /**
         * True iff at the moment of the snapshot, that the observing `Catalog` detected the
         * _possibility_ that the directory has already been opened by another `Catalog` instance,
         * and is currently in use. (It is also possible that the directory was open previously,
         * and a clean shut-down did not occur.)
         */
        Boolean lockedOut.get()
            {
            return error != Null || (info?.status != Closed && info?.stampedBy != this.Catalog.timestamp : False);
            }
        }

    /**
     * Create a snapshot `Glance` of the status of the database on disk.
     *
     * @return a point-in-time snap-shot of the status of the database on disk
     */
    Glance glance()
        {
        SysInfo?   info  = Null;
        Lock?      lock  = Null;
        Exception? error = Null;

        import ecstasy.fs.FileNotFound;

        Byte[]? bytes = Null;
        try
            {
            if (lockFile.exists)
                {
                // this is not an atomic operation, so a FileNotFound may still occur
                bytes = lockFile.contents;
                }
            }
        catch (FileNotFound e)
            {
            // it's ok for the lock file to not exist
            }
        catch (Exception e)
            {
            error = e;
            }

        try
            {
            lock = fromBytes(Lock, bytes?);
            }
        catch (Exception e)
            {
            error ?:= e;
            }

        bytes = Null;
        try
            {
            if (statusFile.exists)
                {
                // this is not an atomic operation, so a FileNotFound may still occur
                bytes = statusFile.contents;
                }
            }
        catch (FileNotFound e)
            {
            // it's ok for the status file to not exist
            }
        catch (Exception e)
            {
            error ?:= e;
            }

        try
            {
            info = fromBytes(SysInfo, bytes?);
            }
        catch (Exception e)
            {
            error ?:= e;
            }

        return new Glance(info, lock, error);
        }


    // ----- catalog lock and status file management -----------------------------------------------

    /**
     * The file used to indicate a short-term lock.
     */
    @Lazy File lockFile.calc()
        {
        return dir.fileFor("sys.lock");
        }

    @Lazy Directory sysDir.calc()
        {
        return dir.dirFor("sys");
        }

    protected Closeable lock(Boolean ignorePreviousLock)
        {
        String           path  = lockFile.path.toString();
        Lock             lock  = new Lock(this);
        immutable Byte[] bytes = toBytes(lock);

        if (lockFile.exists && !ignorePreviousLock)
            {
            String msg = $"Lock file ({path}) already exists";
            try
                {
                Byte[] oldBytes = lockFile.contents;
                String text     = oldBytes.all(b -> b >= 32 && b <= 127 || new Char(b).isWhitespace())
                    ? new String(new Char[oldBytes.size](i -> new Char(oldBytes[i])))
                    : oldBytes.toString();
                msg = $"{msg}; Catalog timestamp={timestamp}; lock file contains: {text}";
                }
            catch (Exception e)
                {
                throw new IllegalState(msg, e);
                }

            throw new IllegalState(msg);
            }

        if (!lockFile.create() && !ignorePreviousLock)
            {
            throw new IllegalState($"Failed to create lock file: {path}");
            }

        try
            {
            lockFile.contents = bytes;
            }
        catch (Exception e)
            {
            throw new IllegalState($"Failed to write lock file: {path}", e);
            }

        return new Closeable()
            {
            @Override void close(Exception? cause = Null)
                {
                lockFile.delete();
                }
            };
        }


    // ----- ObjectStore services for each DBObject ------------------------------------------------

    // ----- Client (Connection) management --------------------------------------------------------

    /**
     * Create a new database connection.
     *
     * @param dbUser  (optional) the database user that the connection will be created on behalf of;
     *                defaults to the database super-user
     */
    Connection createConnection(DBUser? dbUser = Null)
        {
        Client<Schema> client = createClient(dbUser ?: DefaultUser);
        registerClient(client);
        return client.conn ?: assert;
        }

    /**
     * Create a `Client` that will access the database represented by this `Catalog`  on behalf of
     * the specified user. This method allows a custom (e.g. code-gen) `Client` implementation to
     * be substituted for the default, whic allows custom schemas and other custom functionality to
     * be provided in a type-safe manner.
     *
     * @param dbUser  the user that the `Client` will represent
     *
     * @return a ne `Client` instance
     */
    Client<Schema> createClient(DBUser dbUser)
        {
        Int clientId = genClientId();
        val metadata = this.metadata;
        return metadata == Null
                ? new Client<Schema>(this, clientId, dbUser, unregisterClient)
                : metadata.createClient(this, clientId, dbUser, unregisterClient);
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
     * @param client  the Client object to register
     */
    protected void unregisterClient(Client client)
        {
        assert client.catalog == this;
        clients.remove(client.id, client);
        }
    }
