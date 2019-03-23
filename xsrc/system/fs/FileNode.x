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
     * True iff the file-node entry actually exists in the directory that contains it.
     */
    @RO Boolean exists;

    /**
     * The date/time at which the file node was created. The value has no meaning for a
     * file-node that does not exist, or in a file system that does not track creation times.
     */
    @RO DateTime created;

    /**
     * The date/time at which the file node was last modified. The value has no meaning for a
     * file-node that does not exist, or in a file system that does not track modification times.
     *
     * Some file systems allow the modified time to be updated.
     */
    DateTime modified;

    /**
     * The date/time at which the file node was last accessed. The value has no meaning for a
     * file-node that does not exist, or in a file system that does not track access times.
     */
    @RO DateTime accessed;

    /**
     * True iff the file node is readable.
     */
    @RO Boolean readable;

    /**
     * True iff the file node is writable.
     */
    @RO Boolean writable;

    /**
     * Create the file-node only iff it does not already exist. This operation is atomic.
     *
     * @return this FileNode iff the file-node did not exist, and now it does
     */
    conditional FileNode create();

    /**
     * Create the file-node if it does not already exist.
     *
     * @return this FileNode iff the file-node now exists
     */
    conditional FileNode ensure();

    /**
     * Delete the file-node if it exists. This operation may not be atomic.
     *
     * @return this FileNode iff the file-node did exist, and now does not
     */
    conditional FileNode delete();

    /**
     * Attempt to rename the file-node.
     *
     * @param name  the new name for the file-node
     *
     * @return the new FileNode, iff the file-node exists and the rename was successful
     */
    conditional FileNode renameTo(String name);

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
