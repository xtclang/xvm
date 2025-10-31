/**
 * A database client for the TestSchema.
 */
service TestClient
        extends jsondb.Client<TestSchema> {

    import jsondb.Catalog;
    import jsondb.Client;
    import jsondb.Client.DBMapImpl;
    import jsondb.Client.DBValueImpl;
    import jsondb.model.DboInfo;
    import jsondb.storage.JsonMapStore;
    import jsondb.storage.JsonValueStore;

    import oodb.DBMap;
    import oodb.DBUser;
    import oodb.DBValue;

    construct(Catalog<TestSchema>    catalog,
              Int                    clientId,
              DBUser                 dbUser,
              Boolean                readOnly = False,
              function void(Client)? notifyOnClose = Null) {
        construct Client(catalog, clientId, dbUser, readOnly, notifyOnClose);
    }

    @Lazy TestSchema testSchema.calc() = conn.as(TestSchema);

    /**
     * The `TestSchema` implementation.
     */
    @Override
    class RootSchemaImpl(DboInfo info)
            implements TestSchema {

        @Override
        DBMap<String, String> mapData.get() = outer.implFor(IDX_MAP_DATA).as(DBMap<String, String>);

        @Override
        JsonMapStore<String, String> getMapStore() {
            assert DBMapImpl<String, String> impl := &mapData.revealAs((protected DBMapImpl<String, String>));
            assert JsonMapStore<String, String> store := impl.&store_.revealAs((protected JsonMapStore<String, String>));
            return store;
        }

        @Override
        DBValue<String> value.get() = outer.implFor(IDX_VALUE).as(DBValue<String>);

        @Override
        JsonValueStore<String> getValueStore() {
            assert DBValueImpl<String> impl := &value.revealAs((protected DBValueImpl<String>));
            assert JsonValueStore<String> store := impl.&store_.revealAs((protected JsonValueStore<String>));
            return store;
        }

        @Override
        DBMap<Int, Person> people.get() = outer.implFor(IDX_PEOPLE).as(DBMap<Int, Person>);

        @Override
        JsonMapStore<Int, Person> getPeopleStore() {
            assert DBMapImpl<Int, Person> impl := &people.revealAs((protected DBMapImpl<Int, Person>));
            assert JsonMapStore<Int, Person> store := impl.&store_.revealAs((protected JsonMapStore<Int, Person>));
            return store;
        }

        @Override
        String toString() = info.toString();
    }
}
