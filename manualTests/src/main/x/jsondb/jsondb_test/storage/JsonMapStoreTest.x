import collections.ArrayOrderedSet;

import jsondb.Catalog;
import jsondb.storage.ObjectStore;
import jsondb.storage.JsonMapStore;
import jsondb.storage.JsonMapStore.History;
import jsondb.storage.JsonMapStore.MapValue;
import jsondb.storage.JsonMapStore.Marker;
import jsondb.storage.JsonMapStore.Marker.OffHeap;
import jsondb.Client.DBObjectImpl;
import jsondb.Client.DBMapImpl;
import jsondb.TxManager;

import test_db.*;

import xunit.annotations.RegisterExtension;
import xunit.annotations.TestInjectables;

import xunit.assertions.assertThrows;

/**
 * General JsonMapStore tests.
 */
class JsonMapStoreTest {

    @RegisterExtension
    static TestClientProvider clientProvider = new TestClientProvider();

    @Test
    void shouldStoreMapData() {
        assert TestClient            client := clientProvider.getClient();
        TestSchema                   schema = client.testSchema;
        JsonMapStore<String, String> store  = schema.getMapStore().as(protected JsonMapStore<String, String>);
        String                       key    = "Foo";
        String                       value  = "Bar";

        assert False == schema.mapData.contains(key);
        assert False == store.load(store.lastCommit, key);

        schema.mapData.put(key, value);
        assert String committed := schema.mapData.get(key);
        assert committed == value;

        assert String storedValue := store.load(store.lastCommit, key);
        assert storedValue == value;
    }

    @Test
    void shouldStoreComplexKey() {
        assert TestClient        client := clientProvider.getClient();
        TestSchema               schema = client.testSchema;
        JsonMapStore<Id, String> store  = schema.getComplexKeyMapStore().as(protected JsonMapStore<Id, String>);
        Id                       key    = new Id("One", "Two");
        String                   value  = "Foo";

        assert False == schema.complexKeyMap.contains(key);
        assert False == store.load(store.lastCommit, key);

        schema.complexKeyMap.put(key, value);
        assert String committed := schema.complexKeyMap.get(key);
        assert committed == value;

        assert String storedValue := store.load(store.lastCommit, key);
        assert storedValue == value;
    }

    @Test
    @DBInit(/resources/test-data/db1)
    void shouldLoadSimpleMapFromOnStart() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema =  client.testSchema;

        assert String value := schema.mapData.get("One");
        assert value == "Value-One";
    }

    @Test
    @DBInit(/resources/test-data/db1)
    void shouldLoadComplexKeyMapFromOnStart() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;
        Id         key    = new Id("One", "Two");
        assert String value := schema.complexKeyMap.get(key);
        assert value == "Foo";
    }

    @Test
    void shouldReturnValueFromValueFromUsingValue() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;
        schema.mapData.put("Foo", "Bar");

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        assert String value := store.valueFrom(1, "Foo", "Bar");
        assert value == "Bar";
    }

    @Test
    void shouldReturnValueFromUsingDeletion() {
        assert TestClient client := clientProvider.getClient();
        TestSchema schema = client.testSchema;
        schema.mapData.put("Foo", "Bar");
        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        assert store.valueFrom(1, "Foo", Marker.Deleted) == False;
    }

    @Test
    void shouldReturnValueFromUsingDisc() {
        assert TestClient client := clientProvider.getClient();

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

        assert Person fromDisc := store.valueFrom(txId, key, Marker.OffHeap);
        assert fromDisc == person;
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="100",
                          JsonMapStore.ConfigMediumModelMaxFiles="200",
                          JsonMapStore.ConfigSmallModelMaxBytes="5000",
                          JsonMapStore.ConfigMediumModelMaxBytes="15000"])
    void shouldCalculateCorrectModelSizeWhenFilesLessThanBytes() {
        assertModelSizes();
    }

    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="5000",
                          JsonMapStore.ConfigMediumModelMaxFiles="15000",
                          JsonMapStore.ConfigSmallModelMaxBytes="100",
                          JsonMapStore.ConfigMediumModelMaxBytes="200"])
    void shouldCalculateCorrectModelSizeWhenFilesGreaterThanThanBytes() {
        assertModelSizes();
    }

    /**
     * Tests the model size calculation. The scenarios are:
     *
     * totalFiles                 totalBytes                 Model
     * 0                          >=0 && <=SmallMax          Small
     * 0                          >SmallMax && <=MediumMax   Medium
     * 0                          >MediumMax                 Large
     * >=0 && <=SmallMax          >=0 && <=SmallMax          Small
     * >=0 && <=SmallMax          >SmallMax && <=MediumMax   Medium
     * >=0 && <=SmallMax          >MediumMax                 Large
     * >SmallMax && <=MediumMax   >=0 && <=SmallMax          Medium
     * >SmallMax && <=MediumMax   >SmallMax && <=MediumMax   Medium
     * >SmallMax && <=MediumMax   >MediumMax                 Large
     * >MediumMax                 >=0 && <=SmallMax          Large
     * >MediumMax                 >SmallMax && <=MediumMax   Large
     * >MediumMax                 >MediumMax                 Large
     */
    private void assertModelSizes() {
        assert TestClient client := clientProvider.getClient();

        TestSchema                schema = client.testSchema;
        JsonMapStore<Int, Person> store  = schema.getPeopleStore()
                                                 .as(protected JsonMapStore<Int, Person>);

        // totalFiles = 0 and totalBytes = 0 == Small
        assert store.checkModelSize(0, 0) == Small;
        // totalFiles = 0 and totalBytes = ConfigSmallModelMaxBytes == Small
        assert store.checkModelSize(0, store.smallModelBytesMax) == Small;
        // totalFiles = 0 and totalBytes > ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(0, store.smallModelBytesMax + 1) == Medium;
        // totalFiles = 0 and totalBytes = ConfigMediumModelMaxBytes == Medium
        assert store.checkModelSize(0, store.mediumModelBytesMax) == Medium;
        // totalFiles = 0 and totalBytes > ConfigMediumModelMaxBytes == Large
        // ToDo change assertions to Large when we add Large model support
        assert store.checkModelSize(0, store.mediumModelBytesMax + 1) == Medium;

        // totalFiles = ConfigSmallModelMaxFiles and totalBytes = 0 == Small
        assert store.checkModelSize(store.smallModelFilesMax, 0) == Small;
        // totalFiles = ConfigSmallModelMaxFiles and totalBytes = ConfigSmallModelMaxBytes == Small
        assert store.checkModelSize(store.smallModelFilesMax, store.smallModelBytesMax) == Small;
        // totalFiles = ConfigSmallModelMaxFiles and totalBytes > ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(store.smallModelFilesMax, store.smallModelBytesMax + 1) == Medium;
        // totalFiles = ConfigSmallModelMaxFiles and totalBytes = ConfigMediumModelMaxBytes == Medium
        assert store.checkModelSize(store.smallModelFilesMax, store.mediumModelBytesMax) == Medium;
        // totalFiles = ConfigSmallModelMaxFiles and totalBytes > ConfigMediumModelMaxBytes == Large
        // ToDo change assertions to Large when we add Large model support
        assert store.checkModelSize(store.smallModelFilesMax, store.mediumModelBytesMax + 1) == Medium;

        // totalFiles > ConfigSmallModelMaxFiles and totalBytes = 0 == Medium
        assert store.checkModelSize(store.smallModelFilesMax + 1, 0) == Medium;
        // totalFiles > ConfigSmallModelMaxFiles and totalBytes = ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(store.smallModelFilesMax + 1, store.smallModelBytesMax) == Medium;
        // totalFiles > ConfigSmallModelMaxFiles and totalBytes > ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(store.smallModelFilesMax + 1, store.smallModelBytesMax + 1) == Medium;
        // totalFiles > ConfigSmallModelMaxFiles and totalBytes = ConfigMediumModelMaxBytes == Medium
        assert store.checkModelSize(store.smallModelFilesMax + 1, store.mediumModelBytesMax) == Medium;
        // totalFiles > ConfigSmallModelMaxFiles and totalBytes > ConfigMediumModelMaxBytes == Large
        // ToDo change assertions to Large when we add Large model support
        assert store.checkModelSize(store.smallModelFilesMax + 1, store.mediumModelBytesMax + 1) == Medium;

        // totalFiles = ConfigMediumModelMaxFiles and totalBytes = 0 == Medium
        assert store.checkModelSize(store.mediumModelFilesMax, 0) == Medium;
        // totalFiles = ConfigMediumModelMaxFiles and totalBytes = ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(store.mediumModelFilesMax, store.smallModelBytesMax) == Medium;
        // totalFiles = ConfigMediumModelMaxFiles and totalBytes > ConfigSmallModelMaxBytes == Medium
        assert store.checkModelSize(store.mediumModelFilesMax, store.smallModelBytesMax + 1) == Medium;
        // totalFiles = ConfigMediumModelMaxFiles and totalBytes = ConfigMediumModelMaxBytes == Medium
        assert store.checkModelSize(store.mediumModelFilesMax, store.mediumModelBytesMax) == Medium;
        // totalFiles = ConfigMediumModelMaxFiles and totalBytes > ConfigMediumModelMaxBytes == Large
        // ToDo change assertions to Large when we add Large model support
        assert store.checkModelSize(store.mediumModelFilesMax, store.mediumModelBytesMax + 1) == Medium;

        // totalFiles > ConfigMediumModelMaxFiles and totalBytes = 0 == Large
        // ToDo change all assertions to Large when we add Large model support
        assert store.checkModelSize(store.mediumModelFilesMax + 1, 0) == Medium;
        // totalFiles > ConfigMediumModelMaxFiles and totalBytes = ConfigSmallModelMaxBytes == Large
        assert store.checkModelSize(store.mediumModelFilesMax + 1, store.smallModelBytesMax) == Medium;
        // totalFiles > ConfigMediumModelMaxFiles and totalBytes > ConfigSmallModelMaxBytes == Large
        assert store.checkModelSize(store.mediumModelFilesMax + 1, store.smallModelBytesMax + 1) == Medium;
        // totalFiles > ConfigMediumModelMaxFiles and totalBytes = ConfigMediumModelMaxBytes == Large
        assert store.checkModelSize(store.mediumModelFilesMax + 1, store.mediumModelBytesMax) == Medium;
        // totalFiles > ConfigMediumModelMaxFiles and totalBytes > ConfigMediumModelMaxBytes == Large
        assert store.checkModelSize(store.mediumModelFilesMax + 1, store.mediumModelBytesMax + 1) == Medium;
    }

    @Test
    void shouldStoreValueOffHeapForMediumModel() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;
        Person     person = new Person("One", "Two", "Three");
        Int        key    = 19;

        JsonMapStore<Int, Person> store = schema.getPeopleStore().as(protected JsonMapStore<Int, Person>);

        // initialize the people map otherwise forcing the model type will fail
        assert schema.people.size == 0;
        // Force the model to be Medium
        store.model = Medium;

        schema.people.put(key, person);
        // the value should be stored OffHeap
        assert store.isValueOffHeap(key);
        assert Person stored := schema.people.get(key);
        assert stored == person;
        // the read should not have loaded the value back into the heap
        assert store.isValueOffHeap(key);
    }

    @Test
    void shouldBeEmptyOnCreation() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;
        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        assert store.model == Empty;
    }

    @Test
    void shouldTransitionFromEmptyToSmall() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        schema.mapData.put("One", "Value-One");
        assert store.model == Small;
        assert store.isValueOffHeap("One") == False;
    }

    @Test
    void shouldTransitionFromSmallToMedium() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        // force the small file limit to be one.
        store.smallModelFilesMax = 1;

        // adding first entry should remain small
        schema.mapData.put("One", "Value-One");
        assert store.model == Small;
        assert store.isValueOffHeap("One") == False;

        // adding another entry should transition to medium
        schema.mapData.put("Two", "Value-Two");
        assert store.model == Medium;
        assert store.isValueOffHeap("Two");

        // entry "One" will still be in memory as we lazily move to disc
        assert store.isValueOffHeap("One") == False;
        // after updating entry "one" it should now be on disc
        schema.mapData.put("One", "Value-One-Updated");
        assert store.isValueOffHeap("One");
    }

    @Test
    void shouldMoveValuesOffHeapAfterMaintenance() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        // force the small file size limit so the store transitions to a Medium model.
        store.smallModelBytesMax = 24000;
        Catalog<TestSchema> catalog = schema.catalog.as(protected Catalog<TestSchema>);
        TxManager<TestSchema> txMgr = schema.txManager.as(protected TxManager<TestSchema>);

        // The keys used to populate the DBMap, which should result in four data files.
        String[] keySetOne = ["A1A", "A2A", "A3A", "B1A", "B2A", "B3A"];
        String[] keySetTwo = ["C1A", "C2A", "C3A", "D1A", "D2A", "D3A"];

        // Populate the DBMap, saving off some of the transaction ids
        Int[] inUseTxIds = new Array<Int>();
        for (Int i : 0..25) {
            String value = $"Value-{i}";
            for (String key : keySetOne) {
                schema.mapData.put(key, value);
            }
        }
        for (Int i : 26..50) {
            String value = $"Value-{i}";
            for (String key : keySetOne) {
                schema.mapData.put(key, value);
                inUseTxIds.add(store.lastCommit);
            }
        }
        for (Int i : 0..25) {
            String value = $"Value-{i}";
            for (String key : keySetTwo) {
                schema.mapData.put(key, value);
                inUseTxIds.add(store.lastCommit);
            }
        }
        for (Int i : 26..50) {
            String value = $"Value-{i}";
            for (String key : keySetTwo) {
                schema.mapData.put(key, value);
                inUseTxIds.add(store.lastCommit);
            }
        }

        // There should have been enough data to transition to a Medium model
        assert store.model == Medium;

        // Some of the data for the any of the keys and inUseTxIds should still be on-heap
        Boolean anyOnHeap = False;
        for (Int txId : inUseTxIds) {
            if (Boolean offHeap := store.isValueOffHeap(txId, "A1A"), offHeap == False) {
                anyOnHeap = True;
                break;
            }
        }
        assert anyOnHeap == True;

        // Force clean-up (this would normally be done as part of the TxManager maintenance phase)
        ArrayOrderedSet<Int> txSet = new ArrayOrderedSet<Int>(txMgr.byReadId.keys.toArray(Constant));
        store.retainTx(txSet);

        // All data should now be off-heap
        for (String key : keySetOne) {
            // the value should be in the DBMap
            assert String value := schema.mapData.get(key);
            // All history for the key should be OffHeap
            for (Int txId : inUseTxIds) {
                if (Boolean offHeap := store.isValueOffHeap(txId, key)) {
                    assert offHeap == True as $"Key {key} txId {txId} is still on-heap";
                }
            }
        }

        // Force clean-up, including file clean-up (this would normally be done as part of the TxManager maintenance phase)
        store.retainTx(txSet, True);
        // the file size should have reduced an the model should now be Small
        assert store.model == Small;
        // entries should still be OffHeap
        assert store.isValueOffHeap("A1A");
        // now the model is Small, reading an entry will load it back to the heap
        assert String s := schema.mapData.get("A1A");
        assert store.isValueOffHeap("A1A") == False;
    }

    /**
     * This test uses a database pre-loaded with data from the
     * specified location in the `DBInit` annotation and the
     * specified catalog options supplier.
     *
     * The catalog options will configure the TestSchema DBMaps to transition
     * to a Medium model when they use more than a single data file. As the
     * file location specified contains multiple data files for the mapData
     * DBMap, the model should transition to Medium on loading.
     */
    @Test
    @DBInit(/resources/test-data/db1)
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles="1",
                          JsonMapStore.ConfigMediumModelMaxFiles="100"])
    void shouldBeMediumOnOpening() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;

        JsonMapStore<String, String> store = schema.getMapStore().as(protected JsonMapStore<String, String>);
        // the store will be initialized but no data loaded, it knows its file count and bytes size though,
        // so should have the correct model size
        assert store.model == Medium;
        // data is not loaded until accessed, so accessing the size property will load the data files
        assert schema.mapData.size == 2;
        assert store.isValueOffHeap("One");
        assert store.isValueOffHeap("Two");
    }

    @Test
    void shouldLoadValueBackIntoMemoryWhenModelIsSmall() {
        assert TestClient client := clientProvider.getClient();

        TestSchema schema = client.testSchema;
        Person     person = new Person("One", "Two", "Three");
        Int        key    = 19;

        JsonMapStore<Int, Person> store = schema.getPeopleStore().as(protected JsonMapStore<Int, Person>);

        // initialize the people map otherwise forcing the model type will fail
        assert schema.people.size == 0;
        // Force the model to be Medium
        store.model = Medium;

        schema.people.put(key, person);
        // should have gone to disc
        assert store.isValueOffHeap(key);

        // Force the model to be Small
        store.model = Small;

        assert Person stored := schema.people.get(key);
        assert stored == person;
        // get the value should load it back into memory
        assert store.isValueOffHeap(key) == False;
    }
}
