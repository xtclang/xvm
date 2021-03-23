import model.DBObjectInfo;

import oodb.Connection;
import oodb.DBUser;
import oodb.RootSchema;
import oodb.Transaction;


/**
 * Metadata for the catalog for a specific database. This interface is implemented by the `jsonDB`
 * auto-generated code for a database module. The information is necessary to bootstrap the layout
 * and storage of the database into existence, and to provide the various application-specific
 * type mapping for the JSON data in the database.
 *
 * The basis for a custom CatalogMetadata is the database definition (aka "DDL") provided as an
 * Ecstasy module, and which follows a set of rules defined by the `oodb` library. The `jsonDB`
 * library contains tooling that produces source code for a module that binds the DDL to the
 * `jsonDB` database implementation. Specifically, the resulting module provides an implementation
 * of both `Connection` and `DBTransaction`
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
