import oodb.Connection;
import oodb.DBUser;
import oodb.RootSchema;
import oodb.Transaction;


/**
 * Metadata for a specific in-memory database. This interface is implemented by the `imdb`
 * auto-generated code for a database module. The information is necessary to bootstrap the layout
 * of the database into existence.
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
    @Abstract @RO Map<String, DBObjectInfo> dbObjectInfos;

    /**
     * The `Catalog`. Unlike jsonDB, the in-memory DB has a single catalog instance.
     */
    @Lazy Catalog catalog.calc()
        {
        Catalog catalog = Catalog; // the catalog is a singleton
        catalog.initialize(this);
        return catalog;
        }

    typedef (Connection<Schema>  + Schema) ClientConnection;
    typedef (Transaction<Schema> + Schema) ClientTransaction;

    /**
     * The `ClientConnection` factory. This is called by the host when a connection is injected.
     *
     * @return a new `ClientConnection` of the database represented by the `Catalog`
     */
    ClientConnection createConnection(DBUser user)
        {
        Client<Schema> client = catalog.createClient(user).as(Client<Schema>);
        return client.conn ?: assert;
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
    Client<Schema> createClient(Int                    clientId,
                                DBUser                 dbUser,
                                Boolean                readOnly      = False,
                                function void(Client)? notifyOnClose = Null);
    }
