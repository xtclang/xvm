module AddressBookDB_auto
    {
    package db import oodb.xtclang.org;
    package imdb import imdb;
    package AddressBookDB import AddressBookDB;

    import AddressBookDB.AddressBookSchema;

    typedef (db.Connection<AddressBookSchema> + AddressBookSchema) Connection;

    // !!! TEMPORARY !!!
    Connection simulateInjection()
        {
        Connection connection = ServerAddressBookSchema.createConnection();
        return &connection.maskAs<Connection>();
        }
    }