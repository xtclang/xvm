import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

class ClientAddressBookSchema
        extends imdb_.ClientRootSchema
        implements AddressBookSchema_
        implements oodb_.Connection<AddressBookSchema_>
    {
    construct()
        {
        construct imdb_.ClientRootSchema(ServerAddressBookSchema);
        }

    // schema properties
    @Override @Lazy AddressBookDB_.Contacts contacts.calc()
        {
        return new ClientContacts(ServerAddressBookSchema.contacts);
        }
    @Override @Lazy AddressBookDB_.db.DBCounter requestCount.calc()
        {
        return new ClientDBCounter(ServerAddressBookSchema.requestCount);
        }


    // ClientDB* classes
    class ClientContacts
            extends imdb_.ClientDBMap<String, AddressBookDB_.Contact>
            incorporates AddressBookDB_.Contacts
        {
        construct(ServerAddressBookSchema.ServerContacts dbMap)
            {
            construct imdb_.ClientDBMap(dbMap, checkAutoCommit);
            }

        // ----- mixin methods ---------------------------------------------------------------------

        @Override
        void addContact(AddressBookDB_.Contact contact)
            {
            (Boolean autoCommit, oodb_.Transaction tx) = ensureTransaction();

            super(contact);

            if (autoCommit)
                {
                tx.commit();
                }
            }

        @Override
        void addPhone(String name, AddressBookDB_.Phone phone)
            {
            (Boolean autoCommit, oodb_.Transaction tx) = ensureTransaction();

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

        @Override
        class ClientChange
            {
            construct()
                {
                // TODO CP - would be nice if it read "construct super();"
                construct imdb_.ClientDBMap.ClientChange();
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
            extends imdb_.ClientDBCounter
        {
        construct(ServerAddressBookSchema.ServerDBCounter dbCounter)
            {
            construct imdb_.ClientDBCounter(dbCounter, checkAutoCommit);
            }
        }


    // ----- internal API support ------------------------------------------------------------------

    @Override
    @Unassigned oodb_.DBUser dbUser;

    @Override
    public/protected ClientTransaction? transaction;

    @Override
    ClientTransaction createTransaction(
                Duration? timeout = Null, String? name = Null,
                UInt? id = Null, oodb_.DBTransaction.Priority priority = Normal,
                Int retryCount = 0, Boolean readOnly = False)
        {
        import oodb_.Transaction.TxInfo;
        TxInfo txInfo = new TxInfo(timeout, name, id, priority, retryCount, readOnly);

        ClientTransaction tx = new ClientTransaction(txInfo);
        transaction = tx;
        return tx;
        }

    protected (Boolean, oodb_.Transaction) ensureTransaction()
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
            extends imdb_.ClientTransaction<AddressBookSchema_>
            implements AddressBookSchema_
        {
        construct(oodb_.Transaction.TxInfo txInfo)
            {
            construct imdb_.ClientTransaction(
                ServerAddressBookSchema,
                ServerAddressBookSchema.createDBTransaction(),
                txInfo);
            }

        // schema properties
        @Override
        AddressBookDB_.Contacts contacts.get()
            {
            return this.ClientAddressBookSchema.contacts;
            }
        @Override
        AddressBookDB_.db.DBCounter requestCount.get()
            {
            return this.ClientAddressBookSchema.requestCount;
            }


        // transaction API

        @Override
        oodb_.SystemSchema sys.get()
            {
            TODO
            }

        @Override
        (oodb_.Connection<AddressBookSchema_> + AddressBookSchema_) connection.get()
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
