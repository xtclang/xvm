/**
 * Singleton schema.
 */
static service ServerAddressBookSchema
        extends imdb.ServerRootSchema
    {
    construct()
        {
        construct imdb.ServerRootSchema();
        }
    finally
        {
        contacts = new ServerContacts();
        }

    @Unassigned ServerContacts contacts;

    @Inject Clock clock;

    (db.Connection<AddressBookSchema> + AddressBookSchema) createConnection()
        {
        return new ClientAddressBookSchema();
        }

    db.DBTransaction createDBTransaction()
        {
        return new ServerTransaction();
        }

    /**
     * This is the thing that will get injected as Connection. As a child, it's collocated with
     * the IMDB RootSchema service.
     */
    class ClientAddressBookSchema
            extends AddressBookDB.ClientAddressBookSchema
        {
        }

    class ServerContacts
            extends imdb.ServerDBMap<String, Contact>
            incorporates Contacts
        {
        construct()
            {
            construct imdb.ServerDBMap(this.ServerAddressBookSchema, "contacts");
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            TODO
            }
        }

    /**
     * TODO how should this be exposed?
     */
    class ServerTransaction
            implements db.DBTransaction<ServerAddressBookSchema>
        {
        construct()
            {
            status   = Active;
            created  = clock.now;
            priority = Normal;
            contents = new HashMap();
            }

        @Override
        ServerAddressBookSchema schema.get()
            {
            return ServerAddressBookSchema;
            }

        @Override
        Duration transactionTime.get()
            {
            return clock.now - created;
            }

        @Override
        Duration commitTime.get()
            {
            TODO
            }

        @Override
        Int retryCount.get()
            {
            TODO
            }

        @Override
        void addCondition(db.Condition condition)
            {
            TODO
            }
        }
    }