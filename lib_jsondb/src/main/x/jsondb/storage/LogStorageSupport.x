/**
 * A helper class that encapsulates various log-file related functionality.
 *
 * The assumptions are:
 *   - all log files are located in the same data `Directory`;
 *   - the current (active) log file name is "{canonicalName}.json";
 *   - all other log file names have the following format: "{canonicalName}_{timestamp}.json".
 */
const LogStorageSupport {
    construct(Directory dataDir, String canonicalName) {
        this.dataDir        = dataDir;
        this.canonicalName  = canonicalName;
        this.currentLogName = canonicalName + ".json";
    }

    /**
     * The data directory.
     */
    public/private Directory dataDir;

    /**
     * The canonical log file name.
     */
    public/private String canonicalName;

    /**
     * The "current" log file name.
     */
    public/private String currentLogName;

    /**
     * Determine if the passed file is a log file.
     *
     * @param file  a possible log file
     *
     * @return the Time that the LogFile was rotated, or Null if it is the current log file
     */
    conditional Time? isLogFile(File file) {
        String name = file.name;
        if (name == currentLogName) {
            return True, Null;
        }

        Int prefixSize = canonicalName.size;
        if (name.startsWith(canonicalName) && name[prefixSize] == '_' && name.endsWith(".json")) {
            String timestamp = name[prefixSize >..< name.size-5];
            try {
                return True, new Time(timestamp);
            } catch (Exception e) {}
        }

        return False;
    }

    /**
     * Compare two log files for order.
     *
     * @param file1  the first log file
     * @param file2  the second log file
     *
     * @return the order to sort the two files into
     */
    Ordered orderLogFiles(File file1, File file2) {
        assert Time? dt1 := isLogFile(file1);
        assert Time? dt2 := isLogFile(file2);

        // sort the null time to the end, because it represents the "current" log file
        return dt1? <=> dt2? : switch (dt1, dt2) {
            case (Null, Null): Equal;
            case (Null, _): Greater;
            case (_, Null): Lesser;
            default: assert;
        };
    }

    /**
     * Find all of the log files.
     *
     * @return a mutable array of log files, sorted from oldest to current
     */
    File[] findLogs() {
        return dataDir.files()
                .filter(f -> isLogFile(f))
                .sorted(orderLogFiles)
                .toArray(Mutable);
    }
}