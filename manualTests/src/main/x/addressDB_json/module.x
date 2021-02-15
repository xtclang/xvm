module AddressBookDB_jsonDB
        incorporates jsonDB_.CatalogMetadata
    {
    package db_            import oodb.xtclang.org;
    package jsonDB_        import jsonDB.xtclang.org;
    package AddressBookDB_ import AddressBookDB;

    import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

// TODO this should be a mixin into (or sub-class of) Client
    mixin AddressBookSchema_mixin
            // into jsonDB.Client<AddressBookSchema>.Context
            extends RootSchema_mixin
            implements AddressBookSchema
        {
        // is it cached here? or cached by the client? or both?
        @Lazy Contacts contacts.calc()
            {
            return this.Client.ensure(1, create_contacts);
            }
        }

    Contacts create_contacts()
        {
        // because "Contacts" is a mixin, we wrap that mixin around a jsonDB DBMapImpl
        return new @Contacts DBMapImpl(..);
        }

    @Override
    @RO Module schemaModule.get()
        {
        return AddressBookDB;
        }

    @Override
    @Lazy immutable jsonDB_.model.DBObjectInfo[] dbObjectInfos.calc()
        {
        return
            [

            new jsonDB_.model.DBObjectInfo("", "", DBSchema, 0, 0, [1]),
            new jsonDB_.model.DBObjectInfo("contacts", "contacts", DBMap, 1, 0, typeParams=["Key"=String, "Value"=AddressBookDB:Contact]),
            ].freeze(True);
        }

    @Override
    Catalog<Schema> createCatalog(Directory dir, Boolean readOnly = False)
        {
        return new Catalog<Schema>(dir, this, readOnly);
        }

    @Override
    Client<Schema> createClient(Catalog<Schema> catalog, Int id, DBUser dbUser, function void(Client)? notifyOnClose = Null)
        {
        return new AddressBookDBClient_<Schema>(catalog, id, dbUser, unregisterClient)
        }

    service AddressBookDBClient_<Schema extends RootSchema>
            extends Client<Schema>
        {
        // TODO ...
        }
    }