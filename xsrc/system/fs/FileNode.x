/**
 * FileNode represents an entry (or a potential entry) in a directory, either a directory or a file.
 */
interface FileNode
    {
    /**
     * The Path of this file-node.
     */
    @RO Path path;

    /**
     * The name of this file-node.
     */
    @RO String name;

    /**
     * True iff the file-node entry actually exists in the directory.
     */
    @RO Boolean exists;

    /**
     * Rename the file-node.
     *
     * @param name  the new name for the file-node
     *
     * @return the new FileNode, iff the file-node exists and the rename was successful
     */
    conditional FileNode rename(String name);

    /**
     * Create the file-node only if it does not exist. This operation is atomic.
     *
     * @return True iff the file-node did not exist, and now it does
     */
    Boolean create();

    /**
     * Delete the file-node if it exists. This operation may not be atomic.
     *
     * @return True iff the file-node did exist, and now does not
     */
    Boolean delete();

    /**
     * The number of bytes represented by the file-node; for a directory, this value may require
     * an extensive calculation, since the size of the directory is the sum of the sizes of its
     * contents.
     */
    @RO Int size;

    typedef function void () Cancellable;

    /**
     * Watch this file-node, and report any events related to it.
     *
     * @param watch  the FileWatcher to invoke when this file-node has a watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watch(FileWatcher watch);
    }
