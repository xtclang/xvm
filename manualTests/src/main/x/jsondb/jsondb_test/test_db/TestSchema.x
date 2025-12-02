import jsondb.Catalog;
import jsondb.TxManager;

import jsondb.model.DboInfo;

/**
 * The database schema used by the tests.
 */
interface TestSchema
        extends oodb.RootSchema {

    /**
     * A simple `DBMap` of `String` keys and values.
     */
    @RO oodb.DBMap<String, String> mapData;

    /**
     * The underlying storage for the `mapData` `DBMap` property.
     */
    jsondb.storage.JsonMapStore<String, String> getMapStore();

    /**
     * The initial value for the `value` property.
     */
    static String VALUE_INITIAL = "Foo";

    /**
     * A simple `String` `DBValue` with an initial value of `VALUE_INITIAL`.
     */
    @RO @oodb.Initial(VALUE_INITIAL) oodb.DBValue<String> value;

    /**
     * The underlying storage for the `value` `DBValue` property.
     */
    jsondb.storage.JsonValueStore<String> getValueStore();

    /**
     * A simple `DBMap` of `Int` keys and `Person` values.
     */
    @RO oodb.DBMap<Int, Person> people;

    /**
     * The underlying storage for the `people` `DBMap` property.
     */
    jsondb.storage.JsonMapStore<Int, Person> getPeopleStore();

    /**
     * A `DBMap` with a complex `Id` object as the key and simple `String` values.
     */
    @RO oodb.DBMap<Id, String> complexKeyMap;

    /**
     * The underlying storage for the `complexKeyMap` `DBMap` property.
     */
    jsondb.storage.JsonMapStore<Id, String> getComplexKeyMapStore();

    /**
     * The Catalog for the test schema.
     */
    @RO Catalog<TestSchema> catalog;

    /**
     * The `TxManager` for the test schema.
     */
    @RO TxManager<TestSchema> txManager;

    /**
     * The index for the `mapData` property `DboInfo`.
     */
    static Int IDX_MAP_DATA = 1;

    /**
     * The index for the `value` property `DboInfo`.
     */
    static Int IDX_VALUE = 2;

    /**
     * The index for the `people` property `DboInfo`.
     */
    static Int IDX_PEOPLE = 3;

    /**
     * The index for the `complexKeyMap` property `DboInfo`.
     */
    static Int IDX_COMPLEX_KEY_MAP = 4;

    /**
     * Returns the array of `DboInfo` instances for this schema.
     */
    static DboInfo[] getDBObjectInfos() {
        return
            [
            new DboInfo(ROOT, DBSchema, 0, 0,
                    [IDX_MAP_DATA, IDX_VALUE,  IDX_PEOPLE, IDX_COMPLEX_KEY_MAP],
                    ["mapData","value","people", "complexKeyMap"],
                    False),

            new DboInfo(Path:/mapData, DBMap, IDX_MAP_DATA, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Key"=String, "Value"=String],
                options=Map<String, immutable>:[]),
            new DboInfo(Path:/value, DBValue, IDX_VALUE, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Value"=String],
                options=Map<String, immutable>:["initial"=VALUE_INITIAL]),
            new DboInfo(Path:/people, DBMap,  IDX_PEOPLE, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Key"=Int, "Value"=Person],
                options=Map<String, immutable>:[]),
            new DboInfo(Path:/complexKeyMap, DBMap,  IDX_COMPLEX_KEY_MAP, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Key"=Id, "Value"=String],
                options=Map<String, immutable>:[]),
            ];
    }
}
