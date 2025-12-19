import jsondb.TxManager;
import jsondb.TxManager.LogFileInfo;

import oodb.Connection;

import test_db.*;

import xunit.annotations.RegisterExtension;
import xunit.annotations.TestInjectables;

/**
 * Tests for the Json DB TxManager transaction log file management.
 */
class TxManagerLogTest {

    @RegisterExtension
    static TestClientProvider clientProvider = new TestClientProvider();

    @Test
    void shouldHaveTxLogAfterCommit() {
        assert TestClient client := clientProvider.getClient();
        TestSchema        schema =  client.testSchema;

        assert TxManager<TestSchema> manager := client.&txManager
                .revealAs((protected TxManager<TestSchema>));

        // put some data in to make sure we have some Tx log entries
        schema.mapData.put("key1", "value1");
        // there should be a single tx log file
        LogFileInfo[] logInfos = manager.getLogFileInfos();
        assert logInfos.size == 1;
        assert logInfos[0].name == TxManager.LogFileName;
        assertLogsExist(logInfos, manager.sysDir);
    }

    @Test
    // inject a small max log file size to force logs to roll
    @TestInjectables(Map:[TxManager.ConfigTxManagerMaxLogSize="300"])
    void shouldRollTransactionLogs() {
        assert TestClient     client := clientProvider.getClient();
        TestSchema            schema =  client.testSchema;

        assert TxManager<TestSchema> manager := client.&txManager
                .revealAs((protected TxManager<TestSchema>));

        // put some data in to make sure we have some Tx log entries
        schema.mapData.put("key1", "value1");
        // there should be a single tx log file
        LogFileInfo[] logInfos = manager.getLogFileInfos();
        assert logInfos.size == 1;
        assertLogsExist(logInfos, manager.sysDir);

        // put some data in to roll the logs
        schema.mapData.put("key2", "value2");
        schema.mapData.put("key3", "value3");
        logInfos = manager.getLogFileInfos();
        assert logInfos.size > 1;
    }

    @Test
    // inject a very small max log file size to force logs to roll every transaction
    @TestInjectables(Map:[TxManager.ConfigTxManagerMaxLogSize="10"])
    void shouldNotArchiveInUseTransactionLogs() {
        assert TestClient     client := clientProvider.getClient();
        TestSchema            schema =  client.testSchema;

        assert TxManager<TestSchema> manager := client.&txManager
                .revealAs((protected TxManager<TestSchema>));

        // put some data in to make sure we have some Tx log entries
        Int txCount = 10;
        for (Int i : 0 ..< txCount) {
            schema.mapData.put("key1", $"value-{i}");
        }

        // there should be a one log file per transaction plus an empty current log
        LogFileInfo[] logInfosBefore = manager.getLogFileInfos();
        Int           logCountBefore = logInfosBefore.size;
        assert logCountBefore == txCount + 1;
        assertLogsExist(logInfosBefore, manager.sysDir);

        // trigger log file cleanup
        // we need to have a current transaction to trigger cleanup
        Connection<TestSchema> conn = client.ensureConnection();
        using (conn.createTransaction()) {
            schema.mapData.get("key1"); // this will create a readId in TxManager
            manager.cleanupLogfiles();
        }

        // there should be two log files left, the empty current file and the
        // previous timestamped file
        LogFileInfo[] logInfosAfter = manager.getLogFileInfos();
        Int           logCountAfter = logInfosAfter.size;
        assert logInfosAfter.size == 2;
        assert logInfosBefore[logCountBefore - 2] == logInfosAfter[0];
        assert logInfosBefore[logCountBefore - 1] == logInfosAfter[1];
        assertLogsExist(logInfosAfter, manager.sysDir);

        // check files are in the archive directory
        Directory archiveDir = manager.sysDir.dirFor(TxManager.LogFileArchiveName);
        for (Int i : 0 ..< (logCountBefore - logCountAfter)) {
            File logFile = archiveDir.fileFor(logInfosBefore[i].name);
            assert logFile.exists as $"Archived tx log file {logFile.name} does not exist";
        }

        // check we did not archive the current log and in-use log
        File logFile1 = archiveDir.fileFor(logInfosAfter[0].name);
        File logFile2 = archiveDir.fileFor(logInfosAfter[1].name);
        assert !logFile1.exists as $"Should not have archived {logFile1}";
        assert !logFile2.exists as $"Should not have archived {logFile2}";

        LogFileInfo[] statusInfos = manager.getStatusFileContent();
        assert statusInfos.size == 2;
    }

    void assertLogsExist(LogFileInfo[] logInfos, Directory dir) {
        for (LogFileInfo logInfo : logInfos) {
            File logFile = dir.fileFor(logInfo.name);
            assert logFile.exists as $"Tx log file {logFile.name} does not exist";
        }
    }

}