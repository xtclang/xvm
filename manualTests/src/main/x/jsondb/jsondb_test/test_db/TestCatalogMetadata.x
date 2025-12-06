/**
 * A `CatalogMetadata` mixin that implements the required methods for
 * the test database.
 */
mixin TestCatalogMetadata
        extends jsondb.CatalogMetadata<TestSchema> {

    import jsondb.Catalog;
    import jsondb.CatalogMetadata;
    import jsondb.Client;
    import jsondb.model.DboInfo;

    import oodb.DBUser;

    @Override
    @RO Module schemaModule.get() {
        return this.as(Module);
    }

    @Override
    @Lazy DboInfo[] dbObjectInfos.calc() {
        return TestSchema.getDBObjectInfos();
    }

    @Override
    Map<String, Type> dbTypes.get() {
        return Map:[];
    }

    @Override
    @Lazy json.Schema jsonSchema.calc() {
        return new json.Schema(
                schemaMappings   = [],
                version          = dbVersion,
                randomAccess     = True,
                enableMetadata   = True,
                enablePointers   = True,
                enableReflection = True,
                typeSystem       = &this.type.typeSystem,
                );
    }

    @Override
    Catalog<TestSchema> createCatalog(Directory dir, Boolean readOnly = False) {
        return new Catalog<TestSchema>(dir.ensure(), this, readOnly);
    }

    @Override
    Client<TestSchema> createClient(
            Catalog<TestSchema>    catalog,
            Int                    clientId,
            DBUser                 dbUser,
            Boolean                readOnly      = False,
            function void(Client)? notifyOnClose = Null) {
        return new TestClient(catalog, clientId, dbUser, readOnly, notifyOnClose);
    }
}
