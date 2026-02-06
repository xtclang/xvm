import ecstasy.mgmt.ResourceProvider;

import jsondb.Catalog;

import oodb.RootSchema;

import oodb.model.User;

import xunit.UniqueId;

/**
 * Provides a connection to a JSON database, creating the database if necessary.
 *
 * @param type      the type of the database schema this provider manages
 * @param dbModule  the module containing the database schema
 */
service JsonDbProvider<Schema extends RootSchema>(Type<Schema> type, Module dbModule)
        implements Closeable {

    /**
     * A type representing a connection to a database with a specific schema type.
     */
    typedef (oodb.Connection<Schema> + Schema) as Connection;

    /**
     * The database catalogs being managed by this provider.
     */
    private Map<UniqueId, Catalog> catalogs = new HashMap();

    /**
     * The database connections being managed by this provider.
     */
    private Map<UniqueId, Connection> connections = new HashMap();

    /**
     * Create a new database connection.
     *
     * @param uniqueId   the UniqueId of the test fixture associated to the connection
     * @param config     the DbConfig to use to configure the database
     * @param parentDir  the parent directory to put the database directory into
     */
    Connection ensureConnection(UniqueId uniqueId, DbConfig config, Directory dir)
            = connections.computeIfAbsent(uniqueId, () -> createConnection(uniqueId, config, dir));

    /**
     * Create a new database catalog.
     *
     * @param uniqueId   the UniqueId of the test fixture associated to the connection
     * @param config     the DbConfig to use to configure the database
     * @param parentDir  the parent directory to put the database directory into
     */
    Catalog ensureCatalog(UniqueId uniqueId, DbConfig config, Directory dir)
            = catalogs.computeIfAbsent(uniqueId, () -> createCatalog(config, dir));

    /**
     * Close any database connection associated with the specified UniqueId.
     *
     * @param uniqueId  the UniqueId associated to the connections to be closed
     */
    void close(UniqueId uniqueId) {
        if (Connection connection := connections.get(uniqueId)) {
            connections.remove(uniqueId);
            close(connection);
        }
    }

    @Override
    void close(Exception? e = Null) {
        for (Connection connection : connections.values) {
            close(connection);
        }
        connections.clear();
        for (Catalog catalog : catalogs.values) {
            close(catalog);
        }
        catalogs.clear();
    }

    /**
     * Create a Connection.
     *
     * @param uniqueId   the UniqueId of the test fixture associated to the connection
     * @param config     the DbConfig to use to configure the database
     * @param parentDir  the parent directory to put the database directory into
     */
    private Connection createConnection(UniqueId uniqueId, DbConfig config, Directory parentDir) {
        Catalog catalog = ensureCatalog(uniqueId, config, parentDir);
        User    user    = new User(1, "admin");
        return catalog.createClient(user).conn.as(Connection);
    }

    /**
     * Create a Catalog for a test database.
     *
     * @param config     the DbConfig to use to configure the database
     * @param parentDir  the parent directory to put the database directory into
     */
    private Catalog createCatalog(DbConfig config, Directory parentDir) {
        @Inject Directory        testOutputRoot;
        @Inject Directory        testOutput;

        String     dbName   = dbModule.simpleName;
        Directory  buildDir = testOutputRoot.dirFor(dbName).ensure();
        Directory  dataDir  = config.shared ? parentDir.dirFor(dbName) : testOutput.dirFor(dbName);

        if (dataDir.exists) {
            dataDir.deleteRecursively();
        }

        Directory? initDataDir = config.dataDir;
        if (initDataDir.is(Directory)) {
            copy(initDataDir, dataDir);
        }

        dataDir.ensure();
        return jsondb.createCatalog(dbName, dataDir, buildDir);
    }

    /**
     * Close a Connections, safely catching and logging any exceptions.
     *
     * @param connection  the Connection to close
     */
    private void close(Connection connection) {
        try {
            connection.close();
        } catch (Exception e2) {
            @Inject Console console;
            console.print($"Exception during closing of {connection}: {e2.message}");
        }
    }

    /**
     * Close a Catalog, safely catching and logging any exceptions.
     *
     * @param catalog  the Catalog to close
     */
    private void close(Catalog catalog) {
        try {
            catalog.close();
        } catch (Exception e2) {
            @Inject Console console;
            console.print($"Exception during closing of {catalog}: {e2.message}");
        }
    }

    /**
     * Recursively copies the contents of the given directory to the given destination.
     *
     * @param src   the source directory
     * @param dest  the destination directory
     *
     * @throws FileAlreadyExists if the destination directory already exists and is not empty
     * @throws FileNotFound      if the source directory does not exist
     */
    private void copy(Directory src, Directory dest) {
        import ecstasy.fs.FileAlreadyExists;
        import ecstasy.fs.FileNotFound;

        if (src.exists) {
            if (dest.exists && dest.size != 0) {
                throw new FileAlreadyExists(dest.path);
            }
            dest.ensure();
            for (File file : src.files()) {
                copy(file, dest.fileFor(file.name));
            }
            for (Directory dir : src.dirs()) {
                copy(dir, dest.dirFor(dir.name));
            }
        } else {
            throw new FileNotFound(src.path);
        }
    }

    /**
     * Copies the contents of the given file to the given destination.
     *
     * @param src   the source file
     * @param dest  the destination file
     *
     * @throws FileAlreadyExists if the destination file already exists
     */
    private void copy(File src, File dest) {
        import ecstasy.fs.FileAlreadyExists;
        if (src.exists) {
            if (dest.exists) {
                throw new FileAlreadyExists(dest.path);
            }
            dest.append(src.contents);
        }
    }
}
