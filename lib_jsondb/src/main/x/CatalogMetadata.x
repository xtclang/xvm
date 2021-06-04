import model.DBObjectInfo;

import oodb.Connection;
import oodb.DBUser;
import oodb.RootSchema;
import oodb.Transaction;


/**
 * This mixin provides metadata about the catalog of a specific database to the `jsonDB` database
 * engine. The various methods and properties of the mixin are almost always implemented by a code
 * generation process that the `jsonDB` database engine uses, when it prepares a database module
 * (any Ecstasy module that incorporates [oodb.Database] and contains a schema) for hosting.
 *
 * The information provided by this mixin allows the `jsonDB` database engine to bootstrap the
 * database from nothing (i.e. creating the database in persistent storage), and to open an existing
 * database that was previous created.
 *
 * The basis for the information provided by a generated CatalogMetadata is a database definition (aka "DDL"), which is often
 * loosely referred to as a database _schema_. The `oodb` library, of which `jsonDB` is an
 * implementation, uses an Ecstasy module as the representation of a database definition.

   which is provided as an
 * Ecstasy module, and which follows a set of rules defined by the `oodb` library. The `jsonDB`
 * library contains tooling that produces source code for a module that binds the DDL to the
 * `jsonDB` database implementation. Specifically, the resulting module provides an implementation
 * of both `Connection` and `DBTransaction`

  the layout
  * and storage of the database into existence, and to provide the various application-specific
  * type mapping for the JSON data in the database.

 */
mixin CatalogMetadata<Schema extends RootSchema>
        into Module
    {
    /**
     * The "DDL" module that defines the database schema. This is the module provided by the
     * application developer, from which the code generation process works. The code generator
     * will provide an implementation of this property.
     */
    @Abstract @RO Module schemaModule;

    /**
     * The tool-generated implementation module, that was generated to provide a JsonDB-specific
     * implementation of a developer-specified database schema.
     */
    @RO Module implModule.get()
        {
        // unless someone provides some custom implementation, "this" *is* the module that is the
        // tool-generated code; i.e. the code for the auto-generated module incorporates this mixin
        return this;
        }

    /**
     * The information about the objects that define the database schema.
     */
    @Abstract @RO DBObjectInfo[] dbObjectInfos;

    /**
     * The information about the types used in the database schema.
     */
    @Abstract @RO Map<String, Type> dbTypes;

    /**
     * The information about the objects that define the database schema.
     */
    @Abstract @RO json.Schema jsonSchema;

    /**
     * The database schema version.
     */
    @RO Version dbVersion.get()
        {
        // default database version is the module's version
        return schemaModule.version;
        }

    /**
     * The `Catalog` factory. This is called by the host and the returned catalog is retained for
     * later use to create connections.
     *
     * @param dir       the database directory, either within which to create a database, or where
     *                  the database described by this catalog metadata already exists
     * @param readOnly  (optional) pass true to open the database in a read-only manner
     *
     * @return a new `Catalog` for accessing (or otherwise managing) the database located in the
     *         specified directory
     */
    Catalog<Schema> createCatalog(Directory dir, Boolean readOnly = False)
        {
        return new Catalog<Schema>(dir, this, readOnly);
        }

    typedef (Connection<Schema> + Schema) ClientConnection;
    typedef (Transaction<Schema> + Schema) ClientTransaction;

    /**
     * The `ClientConnection` factory. This is called by the host when a connection is injected.
     *
     * @param catalog        the `Catalog` describing the database storage location and structure
     * @param dbUser         the `DBUser` that the `Client` will act on behalf of
     *
     * @return a new `ClientConnection` of the database represented by the `Catalog`
     */
    ClientConnection createConnection(Catalog<Schema> catalog, DBUser user)
        {
        return catalog.createClient(user).conn ?: assert;
        }

    /**
     * The `Client` factory. This is called by the Catalog when it creates a client.
     *
     * @param catalog        the `Catalog` describing the database storage location and structure
     * @param clientId       the unique client id
     * @param dbUser         the `DBUser` that the `Client` will act on behalf of
     * @param readOnly       (optional) pass True to indicate that client is not permitted to modify
     *                       any data
     * @param notifyOnClose  (optional) a notification function to call when the `Client` is closed
     *
     * @return a new `Client` of the database represented by the `Catalog`
     */
    Client<Schema> createClient(Catalog<Schema>        catalog,
                                Int                    clientId,
                                DBUser                 dbUser,
                                Boolean                readOnly      = False,
                                function void(Client)? notifyOnClose = Null);
    }
