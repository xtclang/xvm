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

import oodb.Connection;
import oodb.Transaction;

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

    /**
     * Regression test for a `keys.size == size` desync in `JsonMapStore.keysAt()`.
     */
    @Test
    void shouldNotLoseHistoryKeyWhenLastPendingModSortsBeforeIt() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        // Seed committed history with keys that sort AFTER the key inserted below.
        schema.mapData.put("B", "existing-b");
        schema.mapData.put("C", "existing-c");

        Connection<TestSchema> conn = client.ensureConnection();
        using (conn.createTransaction()) {
            // "A" sorts before "B" and "C", and is the only (last) pending mod in this tx.
            schema.mapData.put("A", "new-a");

            Int      sizeInTx = schema.mapData.size;
            String[] keysInTx = schema.mapData.keys.toArray();

            assert keysInTx.size == sizeInTx
                    as $|keysAt() undercounts by one: expected {sizeInTx} keys \
                        |({keysInTx.size} returned) -- "B" was dropped by the merge walk \
                        |because it was the histEntry pulled just before the last pending \
                        |mod ("A") was classified as Greater.
                        ;
            assert keysInTx.contains("A");
            assert keysInTx.contains("B");
            assert keysInTx.contains("C");
        }
    }

    /**
     * Regression test for a `keys.size == size` desync in `JsonMapStore.keysAt()`.
     */
    @Test
    void shouldNotDoubleCountWhenPendingModUpdatesExistingKey() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema = client.testSchema;

        // Seed enough committed keys to exercise an update among many history entries.
        String[] seeded = new String[40](i -> $"key{(i + 10)}");
        for (String k : seeded) {
            schema.mapData.put(k, $"v-{k}");
        }

        // This shape previously failed when the history and modifications were zipper-merged.
        String modKey = "key10";
        assert seeded.contains(modKey);

        Connection<TestSchema> conn = client.ensureConnection();
        using (conn.createTransaction()) {
            schema.mapData.put(modKey, "v-updated");

            Int      sizeInTx = schema.mapData.size;
            String[] keysInTx = schema.mapData.keys.toArray();

            assert keysInTx.size == sizeInTx
                    as $|keysAt() double-counted: size={sizeInTx} but {keysInTx.size} keys \
                        |returned.
                        ;

            // no key emitted twice
            assert new HashSet<String>(keysInTx).size == keysInTx.size
                    as "keysAt() emitted a duplicate key";

            assert sizeInTx == seeded.size;
            for (String k : seeded) {
                assert keysInTx.contains(k);
            }
        }
    }

    /**
     * Regression test for a `keys.size == size` desync in `JsonMapStore` -- distinct from the
     * (fixed) `keysAt()` merge-ordering bug above.
     *
     * When the store grew from the Small to the Medium model, both the `commit()` and the
     * `retainTx()` transition paths rewrote history entries to the `OffHeap` marker
     * *unconditionally* -- including `Deleted` tombstones. Since `OffHeap != Deleted`, every
     * previously-removed key became visible to `keysAt()` again, while `sizeAt()` (which
     * tracks `sizeByTx`) still correctly excluded it, so `keysAt()`'s `assert keys.size ==
     * size` failed and the store stayed desynced (`keysAt() > sizeAt()` by the number of
     * resurrected tombstones).
     *
     * Seen in the field on the hottest DBMap as `"keys.size == size": keys.size=296,
     * size=284` in an accumulated `--jsondb` run (durable log and `sizeByTx` agreed at 284;
     * `history` reported 12 extra live entries -- resurrected deletes).
     *
     * Several `Client`s drive overlapping insert/delete/rollback transactions against one
     * store (with the model thresholds injected low, so it crosses to Medium mid-run) while
     * a reader repeatedly checks, inside a single read transaction, that `mapData.size`
     * equals the iterated key count; `keysAt()`'s own internal assertion also surfaces here.
     */
    @Test
    @TestInjectables(Map:[JsonMapStore.ConfigSmallModelMaxFiles ="10",
                          JsonMapStore.ConfigMediumModelMaxFiles ="2000000",
                          JsonMapStore.ConfigSmallModelMaxBytes  ="200000000",
                          JsonMapStore.ConfigMediumModelMaxBytes ="400000000"])
    void shouldKeepSizeAndKeysConsistentUnderConcurrentTransactions() {
        assert TestClient boot := clientProvider.getClient();
        jsondb.Catalog<TestSchema> catalog = boot.catalog;
        TestSchema                 schema  = boot.testSchema;

        // >10 distinct key-files => store transitions Small -> Medium, so values go
        // off-heap and prepare()/keysAt() must loadValueFromOffHeap() (a real await).
        Int seedCount = 40;
        for (Int i : 0 ..< seedCount) {
            schema.mapData.put($"seed-{i}", "s");
        }
        assert schema.mapData.size == seedCount;

        Int writerCount    = 6;
        Int itersPerWriter = 30;
        Int keysPerTx      = 4;

        TxWorker[] writers = new TxWorker[writerCount]
                (i -> new TxWorker(catalog.createClient().as(TestClient), i));
        TxWorker sampler = new TxWorker(catalog.createClient().as(TestClient), 99);

        @Future Int       badSamples = sampler.checkParity(400);
        @Volatile Int     finished   = 0;
        @Future   Tuple<> writersDone;
        for (Int i : 0 ..< writerCount) {
            @Future Int c = writers[i].runWrites(itersPerWriter, keysPerTx);
            &c.whenComplete((n, e) -> {
                if (++finished == writerCount) { &writersDone.set(()); }
            });
        }

        Tuple<> joined = writersDone;
        Int     bad    = badSamples;

        Int finalSize = schema.mapData.size;
        Int finalKeys = schema.mapData.keys.toArray().size;
        assert finalKeys == finalSize
                as $"final size/keys desynced: size={finalSize} keys={finalKeys}";
        assert bad == 0
                as $"sizeAt()/keysAt() disagreed on {bad} of 400 in-flight reads";
    }

/**
 * Drives its own `Client` so several instances hit one `JsonMapStore` concurrently.
 */
static service TxWorker {
    construct(TestClient client, Int id) {
        this.client = client;
        this.id     = id;
    }

    private TestClient client;
    private Int        id;

    /**
     * `iterations` transactions: each inserts `keysPerTx` fresh keys and (from iteration 2 on)
     * deletes 2 keys committed 2 iterations earlier. Every 3rd transaction rolls back.
     * Returns the net committed key count.
     */
    Int runWrites(Int iterations, Int keysPerTx) {
        Connection<TestSchema> conn   = client.ensureConnection();
        TestSchema             schema = client.testSchema;
        Int committed = 0;
        for (Int i : 0 ..< iterations) {
            Boolean roll = (i % 3) == 2;
            using (val tx = conn.createTransaction()) {
                for (Int j : 0 ..< keysPerTx) {
                    schema.mapData.put($"w{id}-{i}-{j}", "v");
                }
                if (i >= 2) {
                    schema.mapData.remove($"w{id}-{i-2}-0");
                    schema.mapData.remove($"w{id}-{i-2}-1");
                }
                if (roll) {
                    tx.rollbackOnly = True;
                }
            }
            if (!roll) {
                committed += (i >= 2 ? keysPerTx - 2 : keysPerTx);
            }
        }
        return committed;
    }

    /**
     * `samples` times: inside one read transaction (single read view), compare `mapData.size`
     * (→ `sizeAt`) with the iterated key count (→ `keysAt`). Returns the mismatch count;
     * `keysAt()`'s internal `assert keys.size == size` throws straight out if tripped.
     */
    Int checkParity(Int samples) {
        Connection<TestSchema> conn   = client.ensureConnection();
        TestSchema             schema = client.testSchema;
        Int bad = 0;
        for (Int i : 0 ..< samples) {
            using (conn.createTransaction()) {
                Int kc = schema.mapData.keys.toArray().size;
                Int sz = schema.mapData.size;
                if (kc != sz) {
                    ++bad;
                }
            }
        }
        return bad;
    }
}
}
