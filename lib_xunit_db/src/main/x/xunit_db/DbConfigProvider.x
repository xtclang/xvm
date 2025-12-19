import oodb.RootSchema;

/**
 * A provider of test database configurations.
 */
interface DbConfigProvider {
    /**
     * Return the `DbConfig` for the specified schema type.
     *
     * @return True iff this database is configured for the specified schema type.
     * @return the `DbConfig` to use for the specified schema type
     */
    <Schema extends RootSchema> conditional DbConfig configFor(Type<Schema> schema);

    static DbConfigProvider Default = new DefaultDbConfigProvider();

    static const DefaultDbConfigProvider
            implements DbConfigProvider {

        @Override
        <Schema extends RootSchema> conditional DbConfig configFor(Type<Schema> schema) {
            return True, new DbConfig();
        }
    }
}