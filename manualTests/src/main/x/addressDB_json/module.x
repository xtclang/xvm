// TODO discuss how a developer writing the @Database module can provide user information (if at all)
// TODO creation actions (to initially populate the database), upgrade actions, etc.

module AddressBookDB_jsondb
        incorporates jsondb_.CatalogMetadata<AddressBookDB_.AddressBookSchema>
    {
    package oodb_          import oodb.xtclang.org;
    package json_          import json.xtclang.org;
    package jsondb_        import jsondb.xtclang.org;
    package AddressBookDB_ import AddressBookDB;

    import oodb_.DBUser as DBUser_;

    import jsondb_.Catalog            as Catalog_;
    import jsondb_.CatalogMetadata    as CatalogMetadata_;
    import jsondb_.Client             as Client_;
    import jsondb_.model.DBObjectInfo as DBObjectInfo_;

    import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

    @Override
    @RO Module schemaModule.get()
        {
        assert Module m := AddressBookDB_.isModuleImport();
        return m;
        }

    @Override
    @Lazy immutable DBObjectInfo_[] dbObjectInfos.calc()
        {
        return
            [
            new DBObjectInfo_("", "", DBSchema, 0, 0, [1]),
            new DBObjectInfo_("contacts", "contacts", DBMap, 1, 0, typeParams=Map<String, Type>:["Key"=String, "Value"=AddressBookDB_.Contact]),
            ];
        }

    @Override
    Map<String, Type> dbTypes.get()
        {
        return Map:[
            "String"=String,
            "AddressBookDB_:Contact"=AddressBookDB_.Contact,
            ];
        // also TODO CP allow [] to be used as a Map (etc.) constant without "Map:"
        }

    @Override
    @Lazy json_.Schema jsonSchema.calc()
        {
        return new json_.Schema(
                mappings         = [],              // TODO use dbTypes?
                version          = dbVersion,
                randomAccess     = True,
                enableMetadata   = True,
                enablePointers   = True,
                enableReflection = True,
                typeSystem       = &this.actualType.typeSystem,
                );
        }

    @Override
    Catalog_<AddressBookSchema_> createCatalog(Directory dir, Boolean readOnly = False)
        {
        return new Catalog_<AddressBookDB_.AddressBookSchema>(dir, this, readOnly);
        }

    @Override
    Client_<AddressBookSchema_> createClient(
            Catalog_<AddressBookSchema_> catalog,
            Int                          id,
            DBUser_                      dbUser,
            function void(Client_)?      notifyOnClose = Null)
        {
        return new AddressBookDBClient_(catalog, id, dbUser, notifyOnClose);
        }

    service AddressBookDBClient_(Catalog_<AddressBookSchema_> catalog,
                                 Int                          id,
                                 DBUser_                      dbUser,
                                 function void(Client_)?      notifyOnClose = Null)
            extends Client_<AddressBookSchema_>(catalog, id, dbUser, notifyOnClose)
        {
        @Override
        class RootSchemaImpl(DBObjectInfo_ info_)
                implements AddressBookSchema_
            {
            @Override
            AddressBookDB_.Contacts contacts.get()
                {
                return this.AddressBookDBClient_.implFor(1).as(AddressBookDB_.Contacts);
                }
            }
        }
    }