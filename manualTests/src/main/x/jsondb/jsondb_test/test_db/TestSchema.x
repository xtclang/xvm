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
     * The index for the `mapData` property `DboInfo`.
     */
    static Int IDX_MAP_DATA = 1;

    /**
     * The index for the `value`  property `DboInfo`.
     */
    static Int IDX_VALUE = 2;

    /**
     * The index for the `people`  property `DboInfo`.
     */
    static Int IDX_PEOPLE = 3;

    /**
     * Returns the array of `DboInfo` instances for this schema.
     */
    static DboInfo[] getDBObjectInfos() {
        return
            [
            new DboInfo(ROOT, DBSchema, 0, 0, [IDX_MAP_DATA, IDX_VALUE,  IDX_PEOPLE],
                ["mapData","value","people"], False),

            new DboInfo(Path:/mapData, DBMap, IDX_MAP_DATA, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Key"=String, "Value"=String],
                options=Map<String, immutable>:[]),
            new DboInfo(Path:/value, DBValue, IDX_VALUE, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Value"=String],
                options=Map<String, immutable>:["initial"="Foo"]),
            new DboInfo(Path:/people, DBMap,  IDX_PEOPLE, 0,
                transactional=True, typeParamsTypes=Map<String, Type>:["Key"=Int, "Value"=Person],
                options=Map<String, immutable>:[]),
            ];
    }
}
