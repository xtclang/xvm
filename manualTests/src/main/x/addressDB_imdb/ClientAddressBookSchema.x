class ClientAddressBookSchema
        extends imdb.ClientRootSchema
        implements AddressBookDB.AddressBookSchema
        implements db.Connection<AddressBookDB.AddressBookSchema>
    {
    construct()
        {
        construct imdb.ClientRootSchema(ServerAddressBookSchema);
        }

    // schema properties
    @Override @Lazy AddressBookDB.Contacts contacts.calc()
        {
        return new ClientContacts(ServerAddressBookSchema.contacts);
        }
    @Override @Lazy AddressBookDB.db.DBCounter requestCount.calc()
        {
        return new ClientDBCounter(ServerAddressBookSchema.requestCount);
        }


    // ClientDB* classes
    class ClientContacts
            extends imdb.ClientDBMap<String, AddressBookDB.Contact>
            incorporates AddressBookDB.Contacts
        {
        construct(ServerAddressBookSchema.ServerContacts dbMap)
            {
            construct imdb.ClientDBMap(dbMap, checkAutoCommit);
            }

        // ----- mixin methods ---------------------------------------------------------------------

        @Override
        void addContact(AddressBookDB.Contact contact)
            {
            (Boolean autoCommit, db.Transaction tx) = ensureTransaction();

            super(contact);

            if (autoCommit)
                {
                tx.commit();
                }
            }

        @Override
        void addPhone(String name, AddressBookDB.Phone phone)
            {
            (Boolean autoCommit, db.Transaction tx) = ensureTransaction();

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
                assert args.is(Tuple<String, AddressBookDB.Phone>);

                return addPhone(args[0], args[1]);
                }
            throw new UnsupportedOperation(fn.toString());
            }

        @Override
        class ClientChange
            {
            construct()
                {
                // TODO CP - would be nice if it read "construct super();"
                construct imdb.ClientDBMap.ClientChange();
                }
            finally
                {
                ClientTransaction? tx = this.ClientAddressBookSchema.transaction;
                assert tx != Null;
                tx.dbTransaction_.contents.put(dbObject_.dbName, this);
                }
            }
        }
    class ClientDBCounter
            extends imdb.ClientDBCounter
        {
        construct(ServerAddressBookSchema.ServerDBCounter dbCounter)
            {
            construct imdb.ClientDBCounter(dbCounter, checkAutoCommit);
            }
        }


    // ----- internal API support ------------------------------------------------------------------

    @Override
    @Unassigned db.DBUser dbUser;

    @Override
    public/protected ClientTransaction? transaction;

    @Override
    ClientTransaction createTransaction(
                Duration? timeout = Null, String? name = Null,
                UInt? id = Null, db.DBTransaction.Priority priority = Normal,
                Int retryCount = 0)
        {
        import db.Transaction.TxInfo;
        TxInfo txInfo = new TxInfo(timeout, name, id, priority, retryCount);

        ClientTransaction tx = new ClientTransaction(txInfo);
        transaction = tx;
        return tx;
        }

    protected (Boolean, db.Transaction) ensureTransaction()
        {
        ClientTransaction? tx = transaction;
        return tx == Null
                ? (True, createTransaction())
                : (False, tx);
        }

    Boolean checkAutoCommit()
        {
        return transaction == Null;
        }

    class ClientTransaction
            extends imdb.ClientTransaction<AddressBookDB.AddressBookSchema>
            implements AddressBookDB.AddressBookSchema
        {
        construct(db.Transaction.TxInfo txInfo)
            {
            construct imdb.ClientTransaction(
                ServerAddressBookSchema,
                ServerAddressBookSchema.createDBTransaction(),
                txInfo);
            }

        // schema properties
        @Override
        AddressBookDB.Contacts contacts.get()
            {
            return this.ClientAddressBookSchema.contacts;
            }
        @Override
        AddressBookDB.db.DBCounter requestCount.get()
            {
            return this.ClientAddressBookSchema.requestCount;
            }


        // transaction API

        @Override
        db.SystemSchema sys.get()
            {
            TODO
            }

        @Override
        (db.Connection<AddressBookDB.AddressBookSchema> + AddressBookDB.AddressBookSchema) connection.get()
            {
            return this.ClientAddressBookSchema;
            }

        @Override
        Boolean pending.get()
            {
            return this.ClientAddressBookSchema.transaction == this;
            }

        @Override
        Boolean commit()
            {
            try
                {
                return super();
                }
            finally
                {
                this.ClientAddressBookSchema.transaction = Null;
                }
            }

        @Override
        void rollback()
            {
            try
                {
                super();
                }
            finally
                {
                this.ClientAddressBookSchema.transaction = Null;
                }
            }
        }
    }
