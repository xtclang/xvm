module AddressBookDB_imdb
        incorporates imdb_.CatalogMetadata<AddressBookSchema_>
    {
    package oodb_ import oodb.xtclang.org;
    package imdb_ import imdb.xtclang.org;

    import oodb_.DBUser as DBUser_;

    import imdb_.Client       as Client_;
    import imdb_.DBObjectInfo as DBObjectInfo_;

    package AddressBookDB_ import AddressBookDB;

    import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

    @Override
    @RO Module schemaModule.get()
        {
        assert Module m := AddressBookDB_.isModuleImport();
        return m;
        }

    @Override
    @Lazy Map<String, DBObjectInfo_> dbObjectInfos.calc()
        {
        return Map:
            [
            "" = new DBObjectInfo_("", DBSchema, oodb_.DBSchema),
            "contacts" = new DBObjectInfo_("contacts", DBMap, AddressBookDB_.Contacts),
            "requestCount" = new DBObjectInfo_("requestCount", DBCounter, AddressBookDB_.oodb.DBCounter),

            ];
        }

    @Override
    Client_<AddressBookSchema_> createClient(
            Int                     clientId,
            DBUser_                 dbUser,
            Boolean                 readOnly = False,
            function void(Client_)? notifyOnClose = Null)
        {
        return new AddressBookDBClient_(clientId, dbUser, readOnly, notifyOnClose);
        }

    service AddressBookDBClient_(
            Int                     clientId,
            DBUser_                 dbUser,
            Boolean                 readOnly = False,
            function void(Client_)? notifyOnClose = Null)
                extends Client_<AddressBookSchema_>(clientId, dbUser, readOnly, notifyOnClose)
        {
        @Override
        class RootSchemaImpl(imdb_.Catalog.SchemaStore store_)
                implements AddressBookSchema_
            {
            @Override
            AddressBookDB_.Contacts contacts.get()
                {
                return this.AddressBookDBClient_.implFor("contacts").as(AddressBookDB_.Contacts);
                }

            @Override
            AddressBookDB_.oodb.DBCounter requestCount.get()
                {
                return this.AddressBookDBClient_.implFor("requestCount").as(AddressBookDB_.oodb.DBCounter);
                }
            }

        @Override
        Client_.DBObjectImpl createImpl(String id)
            {
            switch (id)
                {
                case "contacts":
                    return new ContactsImpl_(this.AddressBookDBClient_.storeFor("contacts")
                        .as(imdb_.storage.MapStore<String, AddressBookDB_.Contact>));
                }

            return super(id);
            }

        class ContactsImpl_(imdb_.storage.MapStore<String, AddressBookDB_.Contact> store_)
                extends DBMapImpl<String, AddressBookDB_.Contact>(store_)
                incorporates AddressBookDB_.Contacts
            {
            // ----- mixin methods ---------------------------------------------------------------------

            @Override
            void addContact(AddressBookDB_.Contact contact)
                {
                using (this.AddressBookDBClient_.ensureTransaction(this))
                    {
                    return super(contact);
                    }
                }

            @Override
            void addPhone(String name, AddressBookDB_.Phone phone)
                {
                using (this.AddressBookDBClient_.ensureTransaction(this))
                    {
                    return super(name, phone);
                    }
                }

            // ----- DBObject interface ------------------------------------------------------------

            @Override
            Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|Time)? when = Null)
                {
                if (fn == "addContact" && when == Null)
                    {
                    assert args.is(Tuple<AddressBookDB_.Contact>);

                    return addContact(args[0]);
                    }
                if (fn == "addPhone" && when == Null)
                    {
                    assert args.is(Tuple<String, AddressBookDB_.Phone>);

                    return addPhone(args[0], args[1]);
                    }
                throw new Unsupported(fn.toString());
                }
            }
        }
    }