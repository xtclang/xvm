import ecstasy.annotations.Inject.Options;

import jsondb.Catalog;

import oodb.Connection;
import oodb.RootSchema;

import database.json.JsonDbProvider;

import xunit.MethodOrFunction;
import xunit.UniqueId;

import xunit.extensions.AfterAllCallback;
import xunit.extensions.ExecutionContext;
import xunit.extensions.FixtureExecutionCallback;
import xunit.extensions.ResourceLookupCallback;

/**
 * An XUnit test extension that provides database connections for injection into test code.
 */
service DbInjector
        implements AfterAllCallback
        implements ResourceLookupCallback
        implements FixtureExecutionCallback {

    /**
     * A map of database providers keyed by the database schema type.
     */
    private Map<Type, JsonDbProvider> dbByModule = new HashMap();

    /**
     * The database configuration hierarchy.
     */
    private ConfigHolder? configHolder = Null;

    @Override
    conditional Object lookup(Type type, String name, Options opts = Null) {
        if (type.is(Type<RootSchema>)) {
            @Inject ExecutionContext context;
            @Inject Directory        testOutput;

            assert Type schemaType := type.resolveFormalType("Schema");
            assert schemaType.is(Type<RootSchema>);

            DbConfig      config;
            UniqueId      uniqueId;
            Directory     dir;
            ConfigHolder? configHolder = this.configHolder;
            if (configHolder.is(ConfigHolder)) {
                config   = configHolder.configFor(schemaType);
                dir      = configHolder.dir;
                uniqueId = config.shared ? configHolder.uniqueId : context.uniqueId;
            } else {
                config   = new DbConfig();
                dir      = testOutput;
                uniqueId = context.uniqueId;
            }

            JsonDbProvider<schemaType.DataType>? provider
                    = dbByModule.computeIfAbsent(schemaType, () -> createProvider(schemaType));

            Connection conn = provider.ensureConnection(uniqueId, config, dir);
            return True, type.is(Type<Connection>)
                    ? &conn.maskAs<Connection>(type)
                    : &conn.maskAs<RootSchema>(type);
        }
        return False;
    }

    @Override
    void afterAll(ExecutionContext context) {
        dbByModule.values.forEach(provider -> provider.close());
        dbByModule.clear();
    }

    @Override
    void beforeFixtureExecution(ExecutionContext context) {
        @Inject("testOutputRoot") Directory testOutputRoot;

        switch(context.uniqueId.type) {
        case Module:
        case Package:
        case Class:
            Class? testClass = context.testClass;
            assert testClass.is(Class);
            if (testClass.is(DatabaseTest)) {
                Directory dir = xunit.extensions.testDirectoryFor(testOutputRoot, testClass);
                configHolder = new ConfigHolder(context.uniqueId, testClass, dir, configHolder);
            } else if (context.uniqueId.type == Module) {
                Directory dir = xunit.extensions.testDirectoryFor(testOutputRoot, testClass);
                configHolder = new ConfigHolder(context.uniqueId, DbConfigProvider.Default, dir, configHolder);
            }
            break;
        default:
            MethodOrFunction? testMethod = context.testMethod;
            if (testMethod.is(DatabaseTest)) {
                @Inject("testOutput") Directory dir;
                configHolder = new ConfigHolder(context.uniqueId, testMethod, dir, configHolder);
            }
            break;
        }
    }

    @Override
    void afterFixtureExecution(ExecutionContext context) {
        UniqueId uniqueId = context.uniqueId;
        dbByModule.values.forEach(provider -> provider.close(uniqueId));
        ConfigHolder? holder = this.configHolder;
        if (holder.is(ConfigHolder), holder.uniqueId == uniqueId) {
            this.configHolder = holder.parent;
        }
    }

    /**
     * Creates a new database provider for the given schema type.
     */
    private <Schema extends RootSchema>
    JsonDbProvider<Schema> createProvider(Type<Schema> type) {
        assert Class clz  := type.fromClass();
        String path       = clz.path;
        assert Int colon  := path.indexOf(':');
        String moduleName = path[0 ..< colon];
        assert Module dbModule := typeSystem.moduleByQualifiedName.get(moduleName);
        return new JsonDbProvider(type, dbModule);
    }

    /**
     * A holder for database configurations.
     */
    static const ConfigHolder(UniqueId         uniqueId,
                              DbConfigProvider configs,
                              Directory        dir,
                              ConfigHolder?    parent) {

        <Schema extends RootSchema> DbConfig configFor(Type<Schema> schema) {
            if (DbConfig config := configs.configFor(schema)) {
                return config;
            }
            return parent?.configFor(schema) : new DbConfig();
        }
    }
}
