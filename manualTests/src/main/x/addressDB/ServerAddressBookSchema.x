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

    (db.Connection<AddressBookSchema> + AddressBookSchema) createConnection()
        {
        return new ClientAddressBookSchema();
        }

    @Inject Clock clock;

    @Unassigned ServerContacts contacts;

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
            implements db.DBTransaction
        {
        construct()
            {
            status   = Active;
            created  = clock.now;
            priority = Normal;
            contents = new HashMap();
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
        }
    }