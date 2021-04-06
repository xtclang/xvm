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

    typedef (Connection<Schema>  + Schema) ClientConnection;
    typedef (Transaction<Schema> + Schema) ClientTransaction;

    /**
     * The `ClientConnection` factory. This is called by the host when a connection is injected.
     *
     * @return a new `Client` of the database represented by the `Catalog`
     */
    ClientConnection createConnection();
    }
