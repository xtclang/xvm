/**
 * File represents a file in a FileStore.
 */
interface File
    {
    /**
     *
     *
     * @return True iff the file
     */
    Boolean truncate();

    @RO DateTime created;
    @RO DateTime updated;
    @RO DateTime accessed;

    @RO Boolean readable;
    @RO Boolean writable;
    @RO Boolean deletable;

    // TODO - other open options
    FileChannel open(Boolean read=true, Boolean write=true);

    /**
     * ...
     */
    Byte[] contents;
    }
