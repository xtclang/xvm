class JsonValueStoreTest {

    import jsondb.storage.JsonValueStore;

    import test_db.*;

    @Inject Console console;

    @Test
    void shouldCreateStoreWithInitialValue() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        assert schema.value.get() == TestSchema.VALUE_INITIAL;
    }

    @Test
    void shouldStartEmpty() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Empty;
    }

    @Test
    void shouldBeSmallAfterUpdate() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        schema.value.set("Abc");
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Small;
    }

    @Test
    void shouldSetValue() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        schema.value.set("Bar");
        assert schema.value.get() == "Bar";
    }

    /**
     * This test uses a database pre-loaded with data from the
     * specified location in the `DBInit` annotation.
     */
    @Test
    @DBInit(/resources/test-data/db1)
    void shouldLoadPreviousDatabaseFiles() {
        @Inject TestClient client;
        TestSchema schema = client.testSchema;
        assert schema.value.get() == "Bar";
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Small;
    }
}