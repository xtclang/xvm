import AddressBookDB_.AddressBookSchema as AddressBookSchema_;

/**
 * Singleton schema.
 */
static service ServerAddressBookSchema
        extends imdb_.ServerRootSchema
    {
    construct()
        {
        construct imdb_.ServerRootSchema();
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
            extends imdb_.ServerDBMap<String, AddressBookDB_.Contact>
            incorporates AddressBookDB_.Contacts
        {
        construct(String name)
            {
            construct imdb_.ServerDBMap(this.ServerAddressBookSchema, name);
            }

        @Override
        Tuple dbInvoke(String | Function fn, Tuple args = Tuple:(), (Duration|DateTime)? when = Null)
            {
            TODO
            }
        }

    class ServerDBCounter
            extends imdb_.ServerDBCounter
        {
        construct(String name)
            {
            construct imdb_.ServerDBCounter(this.ServerAddressBookSchema, name);
            }
        }


    // ----- internal API support ------------------------------------------------------------------

    @Inject Clock clock;

    (oodb_.Connection<AddressBookSchema_> + AddressBookSchema_) createConnection()
        {
        return new ClientAddressBookSchema();
        }

    oodb_.DBTransaction createDBTransaction()
        {
        return new ServerTransaction();
        }

    /**
     * TODO how should this be exposed?
     */
    class ServerTransaction
            implements oodb_.DBTransaction<ServerAddressBookSchema>
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
        void addCondition(oodb_.Condition condition)
            {
            TODO
            }
        }
    }