module AddressBookDB_imdb
    {
    package db import oodb.xtclang.org;
    package imdb import imdb;
    package AddressBookDB import AddressBookDB;

    import AddressBookDB.AddressBookSchema;


    // ----- Injection support ---------------------------------------------------------------------

    typedef (db.Connection<AddressBookSchema> + AddressBookSchema) Connection;

    Connection createConnection()
        {
        Connection connection = ServerAddressBookSchema.createConnection();
        return &connection.maskAs<Connection>();
        }
    }