import oodb.RootSchema;

/**
 * The configuration for a test database schema.
 *
 * @param scope    the scope of the database.
 * @param dataDir  the directory of files to copy to initialize the test database data
 */
const DbConfig(Scope      scope   = Shared,
               Directory? dataDir = Null) {
    /**
     * A flag indicating whether the database should be shared between tests.
     */
    @Lazy Boolean shared.calc() = scope == Shared;

    /**
     * @return a copy of this `DbConfig` with the specified scope.
     */
    DbConfig withScope(Scope scope) = new DbConfig(scope, this.dataDir);

    /**
     * @return a copy of this `DbConfig` with the specified data directory.
     */
    DbConfig withDataDir(Directory? dataDir) = new DbConfig(this.scope, dataDir);

    /**
     * An enum representing the scope of a test database.
     */
    enum Scope {
        /**
         * A new database is created that will be shared for all tests below the annotated test
         * fixture. For example if the annotated test fixture is a class, all tests in that class
         * will share the same database.
         */
        Shared,
        /**
         * A new database is created for each test method. For example if the annotated test fixture
         * is a class, each test method in the class will have its own database.
         */
        PerTest
    }
}
