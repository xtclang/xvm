import oodb.RootSchema;

/**
 * The configuration for a test database schema.
 *
 * @param Schema       the schema type for the database.
 * @param scope        the scope of the database.
 * @param templateDir  the directory of files to copy to initialize the test database data
 */
const DbConfig(Scope      scope       = Shared,
               Directory? templateDir = Null) {
    /**
     * A flag indicating whether the database should be shared between tests.
     */
    @Lazy Boolean shared.calc() = scope == Shared;

    DbConfig withScope(Scope scope) = new DbConfig(scope, this.templateDir);

    DbConfig withTemplateDir(Directory? templateDir) = new DbConfig(this.scope, templateDir);

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