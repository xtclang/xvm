import model.DboInfo;

import json.Mapping;

/**
 * The abstract base for LogStore implementations.
 */
@Abstract
@Concurrent
service JsonLogStoreBase<Element extends immutable Const>
        extends ObjectStore(catalog, info)
        implements LogStore<Element>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Catalog          catalog,
              DboInfo     info,
              Mapping<Element> elementMapping,
              Duration         expiry,
              Int              truncateSize,
              Int              maxFileSize,
              )
        {
        construct ObjectStore(catalog, info);

        this.jsonSchema     = catalog.jsonSchema;
        this.elementMapping = elementMapping;
        this.expiry         = expiry.notLessThan(MINUTE);
        this.truncateSize   = truncateSize > 0 ? truncateSize.notLessThan(2K) : MaxValue;
        this.maxFileSize    = maxFileSize.notGreaterThan(this.truncateSize/2);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Inject Clock clock;

    /**
     * A cached reference to the JSON schema.
     */
    public/protected json.Schema jsonSchema;

    /**
     * The JSON Mapping for the log elements.
     */
    public/protected Mapping<Element> elementMapping;

    /**
     * The file owned by this LogStore for purpose of its data storage. The LogStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor("log.json");
        }

    /**
     * A LogStorageSupport.
     */
    @Lazy protected LogStorageSupport support.calc()
        {
        return new LogStorageSupport(dataDir, "log");
        }

    /**
     * The duration of time to hold the log information for.
     */
    protected Duration expiry;

    /**
     * The total size of log files to keep before truncation is allowed.
     */
    protected Int truncateSize;

    /**
     * The maximum size log to store in any one log file. If the `truncateSize` is specified
     * (less then Int.MaxValue), this value is held below half of the `truncateSize`, so as the logs
     * accumulate, in addition to the current log file there will be at least two rolled over files.
     */
    protected Int maxFileSize;

    /**
     * The total size of all the existent log files except the current one.
     */
    protected Int rolledSize;

    /**
     * A record of all of the existent rolled-over log files.
     */
    protected File[] rolledFiles = new File[];


    // ----- transaction API exposed to TxManager --------------------------------------------------

    @Override
    void initializeEmpty()
        {
        assert model == Empty;
        }

    @Override
    void loadInitial()
        {
        assert model != Empty;

        // scan the directory for log files
        File[] logFiles   = support.findLogs();
        Int    logCount   = logFiles.size;
        Int    rolledSize = logFiles.map(f -> f.size).reduce(new aggregate.Sum<Int>());

        File file = dataFile;
        if (file.exists)
            {
            Byte[] bytes   = file.contents;
            String jsonStr = bytes.unpackUtf8();

            // the most common corruption is caused by the process termination between "truncate"
            // and "append" call (see JsonNtxLogStore.append), in which case the terminating "\n]"
            // sequence is missing
            if (!jsonStr.endsWith("\n]"))
                {
                file.append("\n]".utf8());
                }
            this.rolledSize  = rolledSize - file.size;
            this.rolledFiles = logFiles.delete(logCount-1);
            }
        else
            {
            this.rolledSize  = rolledSize;
            this.rolledFiles = logFiles;
            }
        }

    @Override
    void unload()
        {
        }


    // ----- internal ------------------------------------------------------------------------------

    protected void rotateLog()
        {
        Time   now         = clock.now;
        String rotatedName = $"log_{now.toString(True)}.json";

        assert File rotatedFile := dataFile.renameTo(rotatedName);

        while (rolledSize > truncateSize)
            {
            // remove the oldest file from the head
            File oldestFile = rolledFiles[0];

            rolledSize -= oldestFile.size;
            rolledFiles = rolledFiles.delete(0);

            oldestFile.delete();
            }

        if (expiry != NONE)
            {
            Int expiryIndex = -1;
            CheckExpiry: for (File file : rolledFiles)
                {
                assert Time? timestamp := support.isLogFile(file), timestamp != Null;
                if (now > timestamp + expiry)
                    {
                    expiryIndex = CheckExpiry.count;
                    rolledSize  -= file.size;
                    file.delete();
                    }
                else
                    {
                    // the files are sorted - most recent at the tail
                    break;
                    }
                }
            if (expiryIndex != -1)
                {
                rolledFiles = rolledFiles.deleteAll(0..expiryIndex);
                }
            }

        rolledSize  += rotatedFile.size;
        rolledFiles += rotatedFile; // the newest goes to the tail
        }
    }