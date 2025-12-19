import oodb.RootSchema;

/**
 * An annotation that can be applied to a test fixture to configure a test database.
 *
 * The test database configurations will be used by any tests in the annotated fixture and its
 * subclasses unless overridden by another annotated fixture.
 *
 * @param scope        the scope of the database.
 * @param templateDir  the path to a directory of files to copy to initialize the test database
 * @param configs      an optional function that returns a database configurations for a given
 *                     schema type
 */
annotation DatabaseTest(DbConfig.Scope  scope       = Shared,
                        Directory?      templateDir = Null,
                        ConfigProvider? configs     = Null)
        implements DbConfigProvider
        into Class | Method | Function {

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
        return True, new DbConfig(scope, templateDir);
    }
}