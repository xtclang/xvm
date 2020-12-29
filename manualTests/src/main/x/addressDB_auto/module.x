module AddressBookDB_auto
    {
    package db import oodb.xtclang.org;
    package imdb import imdb;
    package UserDbApp_ import AddressBookDB;

    import UserDbApp_.Connection;

    // !!! TEMPORARY !!!
    Connection simulateInjection()
        {
        Connection connection = ServerAddressBookSchema.createConnection();
        return &connection.maskAs<UserDbApp_.Connection>();
        }
    }