module AddressBookDB_imdb
        incorporates imdb_.CatalogMetadata<AddressBookSchema_>
    {
    package oodb_ import oodb.xtclang.org;
    package imdb_ import imdb.xtclang.org;

    import oodb_.DBSchema  as DBSchema_;
    import oodb_.DBCounter as DBCounter_;
    import oodb_.DBMap     as DBMap_;
    import oodb_.DBUser    as DBUser_;

    import imdb_.Client             as Client_;
    import imdb_.DBObjectInfo       as DBObjectInfo_;
    import imdb_.DBObjectStore      as DBObjectStore_;
    import imdb_.storage.DBMapStore as DBMapStore_;

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
            "" = new DBObjectInfo_("", DBSchema, DBSchema_),
            "contacts" = new DBObjectInfo_("contacts", DBMap, DBMap_<String, AddressBookDB_.Contact>),
            "requestCount" = new DBObjectInfo_("requestCount", DBCounter, DBCounter_),
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

    service AddressBookDBClient_(Int                     clientId,
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
            oodb_.DBCounter requestCount.get()
                {
                return this.AddressBookDBClient_.implFor("requestCount").as(oodb_.DBCounter);
                }
            }

        // TODO GG: if "<AddressBookSchema_>" is omitted, the "return super(id)" compilation is wrong - uses Call_1T
        @Override
        Client_<AddressBookSchema_>.DBObjectImpl createImpl(String id)
            {
            switch (id)
                {
                case "contacts":
                    return new ContactsImpl_(this.AddressBookDBClient_.storeFor("contacts")
                        .as(DBMapStore_<String, AddressBookDB_.Contact>));
                }

            return super(id);
            }

        class ContactsImpl_(DBMapStore_<String, AddressBookDB_.Contact> store_)
                extends DBMapImpl<String, AddressBookDB_.Contact>(store_)
                incorporates AddressBookDB_.Contacts
            {
            // ----- mixin methods ---------------------------------------------------------------------

            @Override
            void addContact(AddressBookDB_.Contact contact)
                {
                (oodb_.Transaction tx, Boolean autoCommit) = ensureTransaction(this);

                super(contact);

                if (autoCommit)
                    {
                    tx.commit();
                    }
                }

            @Override
            void addPhone(String name, AddressBookDB_.Phone phone)
                {
                (oodb_.Transaction tx, Boolean autoCommit) = ensureTransaction(this);

                super(name, phone);

                if (autoCommit)
                    {
                    tx.commit();
                    }
                }

            // ----- ClientDBMap interface ------------------------------------------------------------

            @Override
            Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
                {
                if (fn == "addPhone" && when == Null)
                    {
                    assert args.is(Tuple<String, AddressBookDB_.Phone>);

                    return addPhone(args[0], args[1]);
                    }
                throw new UnsupportedOperation(fn.toString());
                }
            }
        }
    }