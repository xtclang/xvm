import %appName%.%appSchema%;

/**
 * Singleton schema.
 */
static service Server%appSchema%
        extends imdb.ServerRootSchema
    {
    // custom property type declarations
    // example:
    //     @Unassigned ServerContacts contacts;
    %ServerPropertyDeclarations%

    construct()
        {
        construct imdb.ServerRootSchema();
        }
    finally
        {
        // custom schema property construction
        // example:
        //    contacts = new ServerContacts();
        %ServerPropertyConstruction%
        }

    @Inject Clock clock;

    (db.Connection<%appSchema%> + %appSchema%) createConnection()
        {
        return new Client%appSchema%();
        }

    db.DBTransaction createDBTransaction()
        {
        return new ServerTransaction();
        }

    /**
     * This is the virtual child of the imdb.ServerRootSchema service that will get injected as
     * "Connection".
     */
    class Client%appSchema%
            extends %appName%_imdb.Client%appSchema%
        {
        }

    // custom ServerDB* classes
    // example:
    //    class ServerContacts
    //            extends imdb.ServerDBMap<String, AddressBookDB.Contact>
    //            incorporates AddressBookDB.Contacts
    %ServerChildrenClasses%

    /**
     * TODO how should this be exposed?
     */
    class ServerTransaction
            implements db.DBTransaction<Server%appSchema%>
        {
        construct()
            {
            status   = Active;
            created  = clock.now;
            priority = Normal;
            contents = new HashMap();
            }

        @Override
        Server%appSchema% schema.get()
            {
            return Server%appSchema%;
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