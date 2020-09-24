/**
 * This is the thing that will get injected as Connection.
 */
service ClientAddressBookSchema
        extends imdb.ClientRootSchema
        implements AddressBookSchema
        implements db.Connection<AddressBookSchema>
    {
    construct()
        {
        construct imdb.ClientRootSchema(ServerAddressBookSchema);
        }
    finally
        {
        contacts = new ClientContacts(ServerAddressBookSchema.contacts);
        }

    @Override
    @Unassigned Contacts contacts;

    @Override
    @Unassigned db.DBUser dbUser;

    @Override
    public/protected ClientAddressBookTransaction? transaction;

    @Override
    ClientAddressBookTransaction createTransaction(
                Duration? timeout = Null, String? name = Null,
                UInt? id = Null, db.DBTransaction.Priority priority = Normal,
                Int retryCount = 0)
        {
        ClientAddressBookTransaction tx = new ClientAddressBookTransaction();
        transaction = tx;
        return tx;
        }

    class ClientContacts
            extends imdb.ClientDBMap<String, Contact>
            incorporates Contacts
        {
        construct(ServerAddressBookSchema.ServerContacts contacts)
            {
            construct imdb.ClientDBMap(contacts);
            }

        // ----- Contacts mixin --------------------------------------------------------------------

        @Override
        void addContact(Contact contact)
            {
            (Boolean autoCommit, db.Transaction tx) = ensureTransaction();

            super(contact);

            if (autoCommit)
                {
                tx.commit();
                }
            }

        // ----- class specific --------------------------------------------------------------------

        protected (Boolean, db.Transaction) ensureTransaction()
            {
            ClientAddressBookTransaction? tx = this.ClientAddressBookSchema.transaction;
            return tx == Null
                    ? (True, createTransaction())
                    : (False, tx);
            }

        // ----- ClientDBMap interface ------------------------------------------------------------

        @Override
        Boolean autoCommit.get()
            {
            return this.ClientAddressBookSchema.transaction == Null;
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            if (fn == "addPhone" && when == Null)
                {
                assert args.is(Tuple<String, Phone>);

                return addPhone(args[0], args[1]);
                }
            throw new UnsupportedOperation(fn.toString());
            }
        }

    class ClientAddressBookTransaction
            extends imdb.ClientTransaction<AddressBookSchema>
            implements AddressBookSchema
        {
        construct()
            {
            construct imdb.ClientTransaction(ServerAddressBookSchema);
            }

        @Override
        db.SystemSchema sys.get()
            {
            TODO
            }

        @Override
        (db.Connection<AddressBookSchema> + AddressBookSchema) connection.get()
            {
            return this.ClientAddressBookSchema;
            }

        @Override
        Contacts contacts.get()
            {
            return this.ClientAddressBookSchema.contacts;
            }

        @Override
        Boolean commit()
            {
            this.ClientAddressBookSchema.transaction = Null;
            return super();
            }

        @Override
        void rollback()
            {
            this.ClientAddressBookSchema.transaction = Null;
            TODO
            }
        }
    }
