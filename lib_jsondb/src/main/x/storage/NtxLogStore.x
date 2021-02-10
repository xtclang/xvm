import model.DBObjectInfo;


/**
 * Provides the low-level I/O for a non-transactional (i.e. extra-transactional) counter.
 */
service NtxLogStore(Catalog catalog, DBObjectInfo info, Appender<String> errs)
        extends ObjectStore(catalog, info, errs)
    {
    /**
     * The file owned by this ObjectStore for purpose of its data storage. The ObjectStore may
     * create, modify, and remove this file.
     */
    @Lazy public/private File dataFile.calc()
        {
        return dataDir.fileFor(info.name + ".json");
        }

    // TODO
    }
