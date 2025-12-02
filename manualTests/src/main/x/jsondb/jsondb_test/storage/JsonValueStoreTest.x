import jsondb.storage.JsonValueStore;

import test_db.*;

import xunit.annotations.RegisterExtension;

/**
 * General JsonValueStore tests.
 */
class JsonValueStoreTest {

    @RegisterExtension
    static TestClientProvider clientProvider = new TestClientProvider();

    @Test
    void shouldCreateStoreWithInitialValue() {
        assert TestClient client := clientProvider.getClient();
        TestSchema schema = client.testSchema;
        assert schema.value.get() == TestSchema.VALUE_INITIAL;
    }

    @Test
    void shouldStartEmpty() {
        assert TestClient client := clientProvider.getClient();
        TestSchema schema = client.testSchema;
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Empty;
    }

    @Test
    void shouldBeSmallAfterUpdate() {
        assert TestClient client := clientProvider.getClient();
        TestSchema schema = client.testSchema;
        schema.value.set("Abc");
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Small;
    }

    @Test
    void shouldSetValue() {
        assert TestClient client := clientProvider.getClient();
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
        assert TestClient client := clientProvider.getClient();
        TestSchema schema = client.testSchema;
        assert schema.value.get() == "Bar";
        JsonValueStore<String> store = schema.getValueStore();
        assert store.model == Small;
    }
}