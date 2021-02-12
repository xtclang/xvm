module AddressBookDB_jsonDB
        incorporates jsonDB_.CatalogMetadata
    {
    package db_            import oodb.xtclang.org;
    package jsonDB_        import jsonDB.xtclang.org;
    package AddressBookDB_ import AddressBookDB;

    import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

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
    @RO Class schemaMixin.get()
        {
        return AddressBookSchema_mixin;
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

    Catalog createCatalog(Directory dir, Boolean readOnly = False)
        {
        return new Catalog(dir, this, readOnly);
        }
    }