module %appName%_imdb
    {
    package db import oodb.xtclang.org;
    package imdb import imdb;
    package %appName% import %appName%;

    import %appName%.%appSchema%;

    typedef (db.Connection<%appSchema%> + %appSchema%) Connection;

    // !!! TEMPORARY !!!
    Connection simulateInjection()
        {
        Connection connection = Server%appSchema%.createConnection();
        return &connection.maskAs<Connection>();
        }
    }