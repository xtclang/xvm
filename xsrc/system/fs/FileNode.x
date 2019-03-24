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
     *
     * @throws AccessDenied  if an attempt is made to modify the value, and permission to change the
     *                       metadata for the file has not been granted
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
     * Create the file-node only if and only if it does not already exist. This operation is atomic.
     *
     * @return this FileNode iff the file-node did not exist, and now it does
     *
     * @throws AccessDenied  if the file does not exist, and permission to create the file has
     *                       not been granted
     */
    conditional FileNode create();

    /**
     * Create the file-node if it does not already exist.
     *
     * @return the FileNode
     *
     * @throws AccessDenied  if the file does not exist, and permission to create the file has
     *                       not been granted
     */
    FileNode ensure();

    /**
     * Delete the file-node if and only if it exists. This operation may not be atomic.
     *
     * @return this FileNode iff the file-node did exist, and now does not
     *
     * @throws AccessDenied  if permission to delete the file has not been granted
     */
    conditional FileNode delete();

    /**
     * Attempt to rename the file-node. This operation is atomic.
     *
     * @param name  the new name for the file-node
     *
     * @return the new FileNode, iff the file-node existed and the rename was successful
     *
     * @throws AccessDenied       if permission to rename the file has not been granted
     * @throws FileAlreadyExists  if a file-node already exists with the new name
     */
    conditional FileNode renameTo(String name);

    /**
     * The number of bytes represented by the file-node.
     *
     * For a directory, this value may require an extensive calculation, since the size of the
     * directory is the sum of the sizes of its contents.
     *
     * For a non-existent file-node, the size is always 0.
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
