import jsondb.TxManager;

import test_db.*;

import xunit.annotations.RegisterExtension;
import xunit.annotations.TestInjectables;

import xunit.assertions.assertThrows;

/**
 * Tests to check TxManager configuration and validation.
 */
class TxManagerConfigTest {

    @RegisterExtension
    static TestClientProvider clientProvider = new TestClientProvider();

    TxManager<TestSchema> getTxManager() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema =  client.testSchema;

        assert TxManager<TestSchema> manager := client.&txManager
                .revealAs((protected TxManager<TestSchema>));

        return manager;
    }

    @Test
    void shouldHaveDefaultConfiguration() {
        TxManager<TestSchema> manager = getTxManager();
        assert manager.maxLogSize       == TxManager.DefaultMaxLogSize;
        assert manager.maxLogArchiveAge == TxManager.DefaultMaxLogArchiveAge;
        assert manager.cleanupThreshold == TxManager.DefaultCleanupThreshold;
    }

    @Test
    void shouldNotAllowNegativeMaxLogSize() {
        TxManager<TestSchema> manager = getTxManager();
        assertThrows(IllegalArgument, () -> {
            manager.maxLogSize = -1;
        });
    }

    @Test
    void shouldNotAllowZeroMaxLogSize() {
        TxManager<TestSchema> manager = getTxManager();
        assertThrows(IllegalArgument, () -> {
            manager.maxLogSize = 0;
        });
    }

    @Test
    void shouldNotAllowZeroMaxLogArchiveAge() {
        TxManager<TestSchema> manager = getTxManager();
        assertThrows(IllegalArgument, () -> {
            manager.maxLogArchiveAge = Duration.None;
        });
    }

    @Test
    void shouldNotAllowNegativeCleanupThreshold() {
        TxManager<TestSchema> manager = getTxManager();
        assertThrows(IllegalArgument, () -> {
            manager.cleanupThreshold = -1;
        });
    }

    @Test
    void shouldNotAllowZeroCleanupThreshold() {
        TxManager<TestSchema> manager = getTxManager();
        assertThrows(IllegalArgument, () -> {
            manager.cleanupThreshold = 0;
        });
    }
}