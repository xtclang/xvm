import AddressBookDB.AddressBookSchema;

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
        // schema property constructions
        contacts = new ServerContacts("contacts");
        requestCount = new ServerDBCounter("requestCount");
        }

    // schema property declarations
    @Unassigned ServerContacts contacts;
    @Unassigned ServerDBCounter requestCount;

    /**
     * This is the thing that will get injected as Connection. As a child, it's collocated with
     * the IMDB RootSchema service.
     */
    class ClientAddressBookSchema
            extends AddressBookDB_imdb.ClientAddressBookSchema
        {
        }

    // server classes for DBObjects
    class ServerContacts
            extends imdb.ServerDBMap<String, AddressBookDB.Contact>
            incorporates AddressBookDB.Contacts
        {
        construct(String name)
            {
            construct imdb.ServerDBMap(this.ServerAddressBookSchema, name);
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            TODO
            }
        }

    class ServerDBCounter
            extends imdb.ServerDBCounter
        {
        construct(String name)
            {
            construct imdb.ServerDBCounter(this.ServerAddressBookSchema, name);
            }
        }


    // ----- internal API support ------------------------------------------------------------------

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