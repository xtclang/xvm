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
        TestSchema            schema  = getSchema();
        TxManager<TestSchema> manager = getTxManager();

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
    @TestInjectables(Map:[TxManager.ConfigMaxLogSize="300"])
    void shouldRollTransactionLogs() {
        TestSchema            schema  = getSchema();
        TxManager<TestSchema> manager = getTxManager();

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
    @TestInjectables(Map:[TxManager.ConfigMaxLogSize="10"])
    void shouldNotArchiveInUseTransactionLogs() {
        assert TestClient     client  := clientProvider.getClient();
        TestSchema            schema  =  getSchema();
        TxManager<TestSchema> manager =  getTxManager();

        assert manager := &manager.revealAs((protected TxManager<TestSchema>));

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

    @Test
    // inject a very small max log file size to force logs to roll every transaction
    @TestInjectables(Map:[TxManager.ConfigMaxLogSize="10"])
    void shouldDeleteOldArchivedFiles() {
        assert TestClient     client  := clientProvider.getClient();
        TestSchema            schema  =  getSchema();
        TxManager<TestSchema> manager =  getTxManager();

        assert manager := &manager.revealAs((protected TxManager<TestSchema>));

        // put some data in to make sure we have some Tx log entries
        Int txCount = 10;
        for (Int i : 0 ..< txCount) {
            schema.mapData.put("key1", $"value-{i}");
            // we pause to give the tx files a bit of a gap between modified times
            pause(Duration.Millisec * 500);
        }

        // trigger log file cleanup to create some archived files
        // we need to have a current transaction to trigger cleanup
        Connection<TestSchema> conn = client.ensureConnection();
        using (conn.createTransaction()) {
            schema.mapData.get("key1"); // this will create a readId in TxManager
            manager.cleanupLogfiles();
        }

        // work out the creation times of the archived files
        Directory  archiveDir = manager.sysDir.dirFor(TxManager.LogFileArchiveName);
        File[]     oldFiles   = new Array();
        Time       deleteTime = Time.EPOCH;
        for (File file : archiveDir.files().sorted(TxManagerLogTest.fileOrderer)) {
            oldFiles.add(file);
            deleteTime = file.modified;
            if (oldFiles.size == 3) {
                break;
            }
        }

        // set the max age to be before the oldest archived file
        manager.maxLogArchiveAge = manager.clock.now - deleteTime;

        // put some more data and trigger log file cleanup again
        for (Int i : 0 ..< txCount) {
            schema.mapData.put("key1", $"value-{i}");
        }
        using (conn.createTransaction()) {
            schema.mapData.get("key1"); // this will create a readId in TxManager
            manager.cleanupLogfiles();
        }

        // The files in oldFiles should have been deleted
        for (File file : oldFiles) {
            assert !file.exists as $"Archived tx log file {file.name} should have been deleted";
        }
    }

    /**
     * @return the protected view of the TxManager
     */
    TxManager<TestSchema> getTxManager() {
        assert TestClient            client  := clientProvider.getClient();
        assert TxManager<TestSchema> manager := client.&txManager
                .revealAs((protected TxManager<TestSchema>));
        return manager;
    }

    TestSchema getSchema() {
        assert TestClient client := clientProvider.getClient();
        return client.testSchema;
    }

    /**
     * Verify that the specified transaction logs exist.
     *
     * @param logInfos   the log file infos to check
     * @param dir        the directory the log files should be in
     */
    void assertLogsExist(LogFileInfo[] logInfos, Directory dir) {
        for (LogFileInfo logInfo : logInfos) {
            File logFile = dir.fileFor(logInfo.name);
            assert logFile.exists as $"Tx log file {logFile.name} does not exist";
        }
    }

    static Ordered fileOrderer(File file1, File file2) {
        return file1.modified <=> file2.modified;
    }

    static void pause(Duration duration) {
        @Inject Timer timer;
        @Future Tuple done;
        timer.start();
        timer.schedule(duration, () -> {done = ();});
        return done;
    }

}