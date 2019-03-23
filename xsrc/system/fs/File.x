/**
 * File represents a file in a FileStore.
 */
interface File
        extends FileNode
    {

    /**
     *
     *
     * @return True iff the file
     */
    Boolean truncate();

    /**
     * The contents of the file, as an Array of Byte.
     */
    Byte[] contents;

    // TODO - other open options
    FileChannel open(Boolean read=true, Boolean write=true);
    }
