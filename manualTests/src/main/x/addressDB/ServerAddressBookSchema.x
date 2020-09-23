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
     * TODO should this be exposed?
     */
    class ServerTransaction
        {
        }
    }