import model.DBObjectInfo;


/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
service NtxCounterStore(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(catalog, info, errs)
    {
    Int? current;

    /**
     * The file owned by this ObjectStore for purpose of its data storage. The ObjectStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor(info.name + ".json");
        }

    /**
     * Obtain the current counter value.
     *
     * @param txId  specifies the transaction identifier to use to determine the point-in-time data
     *              stored in the database, as if the value of the singleton were read immediately
     *              after that specified transaction had committed
     *
     * @return the value of the singleton at the completion of the specified transaction
     */
    Int load()
        {
        return current ?:
            {
            File file = dataFile;
            return file.exists
                    ? new IntLiteral(file.contents.unpackString()).toInt64() // TODO file corruption here will cause exception
                    : 0;
            };
        }

    /**
     * Modify the singleton as part of the specified transaction by replacing the value.
     *
     * @param txId   the transaction being committed
     * @param value  the new value for the singleton
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    void store(Int value)
        {
        current = value;
        dataFile.contents = value.toString().utf8();
        }

    /**
     * Modify the counter value _in a relative manner_ by applying the passed delta value to the
     * current value, returning both the value before and after the change.
     *
     * @param delta  the relative value adjustment to make to the counter
     *
     * @return before  the value before the change
     * @return after   the value after the change
     */
    (Int before, Int after) adjust(Int delta = 1)
        {
        Int before = load();
        Int after  = before + delta;
        store(after);
        return before, after;
        }
    }
