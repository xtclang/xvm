import collections.ArrayOrderedSet;

import jsondb.storage.JsonMapStore;

import test_db.*;

import xunit.annotations.RegisterExtension;
import xunit.annotations.TestInjectables;

import xunit.assertions.assertThrows;

/**
 * Tests for JsonMapStore injectable configuration.
 */
class JsonMapStoreConfigTest {

    @RegisterExtension
    static TestClientProvider clientProvider = new TestClientProvider();

    @Test
    void shouldHaveDefaultConfiguration() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema =  client.testSchema;
        JsonMapStore      store  =  schema.getMapStore().as(protected JsonMapStore<String, String>);

        assert store.smallModelBytesMax  == JsonMapStore.DEFAULT_SMALL_MAX_BYTES;
        assert store.smallModelFilesMax  == JsonMapStore.DEFAULT_SMALL_MAX_FILES;
        assert store.mediumModelBytesMax == JsonMapStore.DEFAULT_MEDIUM_MAX_BYTES;
        assert store.mediumModelFilesMax == JsonMapStore.DEFAULT_MEDIUM_MAX_FILES;
    }

    @Test
    void shouldFailToSetStoreSmallBytesMaxToZero() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelBytesMax = 0;
        });
    }

    @Test
    void shouldFailToSetStoreSmallBytesMaxToLessThanZero() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelBytesMax = -19;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxBytes="0"])
    void shouldFailToCreateStoreWithSmallBytesMaxConfigOfZero() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxBytes="-100"])
    void shouldFailToCreateStoreWithSmallBytesMaxConfigOfLessThanZero() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    void shouldFailToSetStoreSmallBytesMaxToEqualMediumBytesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelBytesMax = store.mediumModelBytesMax;
        });
    }

    @Test
    void shouldFailToSetStoreSmallBytesMaxToMoreThanMediumBytesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelBytesMax = store.mediumModelBytesMax + 100;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxBytes="100",
                          JsonMapStore.ConfigMediumModelMaxBytes="100"])
    void shouldFailToCreateStoreWithSmallBytesMaxConfigEqualMediumBytesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxBytes="200",
                          JsonMapStore.ConfigMediumModelMaxBytes="100"])
    void shouldFailToCreateStoreWithSmallBytesMaxConfigMoreThanMediumBytesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    void shouldFailToSetStoreSmallFilesMaxToZero() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelFilesMax = 0;
        });
    }

    @Test
    void shouldFailToSetStoreSmallFilesMaxToLessThanZero() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelFilesMax = -19;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="0"])
    void shouldFailToCreateStoreWithSmallFilesMaxConfigOfZero() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="-100"])
    void shouldFailToCreateStoreWithSmallFilesMaxConfigOfLessThanZero() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    void shouldFailToSetStoreSmallFilesMaxToEqualMediumFilesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelFilesMax = store.mediumModelFilesMax;
        });
    }

    @Test
    void shouldFailToSetStoreSmallFilesMaxToMoreThanMediumFilesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.smallModelFilesMax = store.mediumModelFilesMax + 100;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="100",
                          JsonMapStore.ConfigMediumModelMaxFiles="100"])
    void shouldFailToCreateStoreWithSmallFilesMaxConfigEqualMediumFilesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="200",
                          JsonMapStore.ConfigMediumModelMaxFiles="100"])
    void shouldFailToCreateStoreWithSmallFilesMaxConfigMoreThanMediumFilesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    void shouldFailToSetStoreMediumBytesMaxToLessThanSmallBytesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.mediumModelBytesMax = store.smallModelBytesMax - 1;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigMediumModelMaxBytes="1"])
    void shouldFailToCreateStoreWithSmallBytesMaxConfigOfLessThanSmallBytesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

    @Test
    void shouldFailToSetStoreMediumFilesMaxToLessThanSmallFilesMax() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);

        assertThrows(IllegalArgument, () -> {
            store.mediumModelFilesMax = store.smallModelFilesMax - 1;
        });
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigMediumModelMaxFiles="1"])
    void shouldFailToCreateStoreWithSmallFilesMaxConfigOfLessThanSmallFilesMax() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        assertThrows(IllegalArgument, () -> {
            schema.getMapStore();
        });
    }

}
