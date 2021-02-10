import model.DBObjectInfo;


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
mixin CatalogMetadata
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
     * the class of the mixin for the root schema
     */
    @Abstract @RO Class schemaMixin;

    /**
     * the information about the schema
     */
    @Abstract @RO DBObjectInfo[] dbObjectInfos;



    @RO Version dbVersion.get()
        {
        // default database version is the module's version
        return schemaModule.version;
        }
    }
