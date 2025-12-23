import oodb.RootSchema;

/**
 * An annotation that can be applied to a test fixture to configure a test database.
 *
 * The test database configurations will be used by any tests in the annotated fixture and its
 * subclasses unless overridden by another annotated fixture.
 *
 * The `DatabaseTest` annotation is implements `DbConfigProvider` to provide database configurations
 * for a given schema type. These configurations are used by XUnit DB to initialize the test
 * databases.
 *
 * Database Scope
 * The scope property is used to determine the lifecycle of the test database. The default value of
 * `Singleton` means that a single database instance is shared across all tests in the annotated
 * fixture and its subclasses. Alternatively, a scope of `PerTest` means that a new database
 * instance is created for each test in the annotated fixture and its subclasses.
 *
 * Database Initialisation
 * A test database can be created from a pre initialised set of database files by setting the
 * `dataDir` property to the path of a directory containing the files. The directory must be an
 * embedded resource in the module as the directory property must be a compile time constant.
 *
 * Advanced Database Configuration
 * Setting the `scope` and `dataDir` properties of the `DatabaseTest` annotation is the simplest way
 * to configure a test database. More advanced use cases, for example multiple databases with
 * different configurations or initialising databases with files from the file system can be
 * supported using the `configs` property.
 *
 * The `configs` property specifies a function that optionally returns a `DbConfig` instance to be
 * used to configure a database based on the schema type. The function will be called by the XUnit
 * DB framework whenever it needs to create a database for tests within the annotated fixture. The
 * function can then do whatever it needs at runtime to provide the required `DBConfig` instance.
 *
 * @param scope    the scope of the database.
 * @param dataDir  the path to a directory of files to copy to initialize the test database
 * @param configs  an optional function that returns a database configurations for a given schema
 *                 type
 */
annotation DatabaseTest(DbConfig.Scope  scope   = Shared,
                        Directory?      dataDir = Null,
                        ConfigProvider? configs = Null)
        implements DbConfigProvider
        into Package | Class | Method | Function {

    /**
     * A function that takes a schema type and optionally returns a database configuration for the
     * schema.
     */
    typedef function conditional DbConfig (Type<RootSchema>) as ConfigProvider;

    @Override
    <Schema extends RootSchema> conditional DbConfig configFor(Type<Schema> schema) {
        ConfigProvider? configs = this.configs;
        if (configs.is(ConfigProvider)) {
            if (DbConfig config := configs(schema)) {
                return True, config;
            }
        }
        return True, new DbConfig(scope, dataDir);
    }
}
