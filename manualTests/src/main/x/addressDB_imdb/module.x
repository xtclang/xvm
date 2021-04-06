module AddressBookDB_imdb
        incorporates imdb_.CatalogMetadata<AddressBookSchema_>
    {
    package oodb_ import oodb.xtclang.org;
    package imdb_ import imdb.xtclang.org;
    package AddressBookDB_ import AddressBookDB;

    import oodb_.DBUser as DBUser_;
    import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

    @Override
    @RO Module schemaModule.get()
        {
        assert Module m := AddressBookDB_.isModuleImport();
        return m;
        }

    @Override
    (oodb_.Connection<AddressBookSchema_> + AddressBookSchema_) createConnection()
        {
        return ServerAddressBookSchema.createConnection();
        }
    }