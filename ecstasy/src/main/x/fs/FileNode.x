/**
 * FileNode represents an entry (or a potential entry) in a directory, either a directory or a file.
 */
interface FileNode
    {
    /**
     * The Path of this file-node as it is identified within its FileStore.
     */
    @RO Path path;

    /**
     * The name of this file-node within its directory. (The root directory name may be blank.)
     */
    @RO String name;

    /**
     * True iff the file-node entry actually exists in the directory that contains it.
     */
    @RO Boolean exists;

    /**
     * Determine if this node is a _link_ to another node, and if it is, obtain a reference to the
     * link itself, instead of the node that is linked-to. This allows the link to be deleted, for
     * example, instead of deleting the node that is linked-to.
     *
     * Normally, applications can ignore the detail of whether or not a node is a link (such as a
     * _symbolic link_), because the `Directory` qnd `File` interfaces represent the node that is
     * _linked to_. For example, opening a node linked to a file will open the file that is _linked
     * to_, so the application can read and write data directly from that linked-to file without
     * any knowledge that it is doing so via a link.
     */
    conditional File linkAsFile();

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
     * @return True iff the file-node did not exist, and now it does
     *
     * @throws AccessDenied  if the file does not exist, and permission to create the file has
     *                       not been granted
     */
    Boolean create();

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
     * @return True iff the file-node did exist, and now does not
     *
     * @throws AccessDenied  if permission to delete the file has not been granted
     */
    Boolean delete();

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
     * @param watcher  the FileWatcher to invoke when this file-node has a watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watch(FileWatcher watcher);

    /**
     * Emit a directory-style hierarchical listing to the specified Appender.
     *
     * @param buf        the Appender to emit the listing to
     * @param recursive  (optional) True to have the listing recurse through sub-directories
     * @param indent     (optional) indentation for this item in the hierarchical listing
     */
    Appender<Char> emitListing(Appender<Char> buf, Boolean recursive = False, String indent = "");
    }
