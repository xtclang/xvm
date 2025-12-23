import oodb.RootSchema;

/**
 * A provider of test database configurations.
 */
interface DbConfigProvider {
    /**
     * Return the `DbConfig` for the specified schema type.
     *
     * @return True iff this database is configured for the specified schema type.
     * @return (conditional) the `DbConfig` to use for the specified schema type
     */
    <Schema extends RootSchema> conditional DbConfig configFor(Type<Schema> schema);

    /**
     * A default `DbConfigProvider` instance.
     */
    static DbConfigProvider Default = new DefaultDbConfigProvider();

    /**
     * The default implementation of `DbConfigProvider`, which provides a default `DbConfig` for all
     * schema types.
     */
    static const DefaultDbConfigProvider
            implements DbConfigProvider {

        @Override
        <Schema extends RootSchema> conditional DbConfig configFor(Type<Schema> schema) {
            return True, new DbConfig();
        }
    }
}
