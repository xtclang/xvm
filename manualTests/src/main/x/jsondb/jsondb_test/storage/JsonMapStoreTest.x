class JsonMapStoreTest {

    import jsondb.storage.ObjectStore.DiscStorage;
    import jsondb.storage.JsonMapStore;
    import jsondb.storage.JsonMapStore.DataFileRow;
    import jsondb.storage.JsonMapStore.History;
    import jsondb.storage.JsonMapStore.MapValue;
    import jsondb.Client.DBObjectImpl;
    import jsondb.Client.DBMapImpl;

    import test_db.*;

    @Inject Console console;

    @Test
    void shouldCreateStore() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        assert False == schema.mapData.contains("Foo");
        schema.mapData.put("Foo", "Bar");
        assert String value := schema.mapData.get("Foo");
        assert value == "Bar";
    }

    @Test
    void shouldReturnFromValueFromUsingValue() {
        @Inject TestClient client;

        TestSchema schema = client.testSchema;
        schema.mapData.put("Foo", "Bar");

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        assert String value := store.valueFrom(1, "Foo", "Bar");
        assert value == "Bar";
    }

    @Test
    void shouldReturnValueFromUsingDeletion() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        schema.mapData.put("Foo", "Bar");
        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        assert store.valueFrom(1, "Foo", JsonMapStore.Deletion.Deleted) == False;
    }

    @Test
    void shouldReturnValueFromUsingDisc() {
        @Inject TestClient client;

        TestSchema schema = client.testSchema;
        Person     person = new Person("One", "Two", "Three");
        Int        key    = 19;

        schema.people.put(key, person);
        assert Person stored := schema.people.get(key);
        assert stored == person;

        JsonMapStore<Int, Person> store = schema.getPeopleStore().as(protected JsonMapStore<Int, Person>);
        Int                       txId  = store.lastCommit;

        // Overwrite the entry so we know we are not reading it from memory
        schema.people.put(key, new Person("Four", "Five", "Six"));

        assert Person fromDisc := store.valueFrom(txId, key, DiscStorage.OnDisc);
        assert fromDisc == person;
    }

    @Test
    void shouldStoreValueOnDiscForMediumModel() {
        @Inject TestClient client;

        TestSchema schema = client.testSchema;
        Person     person = new Person("One", "Two", "Three");
        Int        key    = 19;

        JsonMapStore<Int, Person> store = schema.getPeopleStore().as(protected JsonMapStore<Int, Person>);

        // initialize the people map otherwise forcing the model type will fail
        assert schema.people.size == 0;
        // Force the model to be Medium
        store.model = Medium;

        schema.people.put(key, person);
        assert store.isValueOnDisc(key);
        assert Person stored := schema.people.get(key);
        assert stored == person;
    }
}
