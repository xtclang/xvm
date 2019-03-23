/**
 * FileStore represents hierarchical file storage, but at a relatively abstract level.
 *
 * The FileStore interface is not intended to provide all of the capabilities of a file system, in
 * terms of metadata, links, security management, and so on. Rather, it represents the most common
 * operations that an application would need access to, in order to read and write and manage files
 * for the application itself.
 *
 * Additionally, the FileStore interface is designed to be incredibly straight-forward to implement
 * constrained forms thereof.
 */
interface FileStore
    {
    /**
     * The root directory of the FileStore. This is conceptually analogous to "/" in UNIX, for
     * example. However, a FileStore is an abstraction, so its root may not correspond to the the
     * local file system root, and it may not even correspond to the file system at all.
     */
    @RO Directory root;

    /**
     * Obtain the Directory or File at the specified path.
     *
     * @param path   the Path of the Directory or File to find
     *
     * @return the Directory or File at that path, only if is exists
     */
    conditional Directory|File find(Path path);

    /**
     * Obtain a Directory object for the specified path, whether or not the directory actually
     * exists. This method allows the caller to obtain a Directory object that can be watched,
     * created, etc., even if the directory did not previously exist.
     *
     * If a file exists at the location specified by the path, then it will still be possible to
     * obtain a Directory object for the path, but the Directory will not exist, and it will not
     * be able to be created without first deleting the file.
     */
    Directory dirFor(Path path);

    /**
     * Obtain a File object for the specified path, whether or not the file actually exists.
     * This method allows the caller to obtain a File object that can be watched, created, etc.,
     * even if the file did not previously exist.
     *
     * If a directory exists at the location specified by the path, then it will still be possible
     * to obtain a File object for the path, but the file will not exist, and it will not be able
     * to be created without first deleting the directory.
     */
    File fileFor(Path path);

    typedef function void () Cancellable;

    /**
     * Watch a specified path within this FileStore, and report events related to that path.
     *
     * @param path   the Path to watch
     * @param watch  the FileWatcher to invoke when the specified path has a watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watch(Path path, FileWatcher watch);

    /**
     * Watch a specified path and everything under it within this FileStore, and report events
     * related to that path.
     *
     * @param path   the Path to watch
     * @param watch  the FileWatcher to invoke when the specified path has a watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watchRecursively(Path path, FileWatcher watch);

    /**
     * Specifies whether this FileStorage is known to be explicitly read-only.
     */
    @RO Boolean readOnly;

    /**
     * @return a read-only view of this FileStore, or this FileStore if it is read-only
     */
    FileStore ensureReadOnly();

    /**
     * Obtain the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of obtaining a total capacity
     * measurement; for those that do not, this property is likely to yield 0, or the property may
     * be expensive to calculate.
     *
     * This value is considered to be _advisory_, and not definitive.
     */
    @RO Int capacity;

    /**
     * Obtain the used portion of the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of determining a total number of
     * used bytes with near-real-time accuracy; for those that do not, this property is likely to
     * be expensive to calculate, because it may have to sum up the space used in each directory by
     * each file.
     *
     * It is possible that capacitiy, bytesUsed, and bytesFree all provide information, yet the
     * capacity value does not match the sum of the bytesUsed and bytesFree values. This value is
     * considered to be _advisory_, and not definitive.
     */
    @RO Int bytesUsed;

    /**
     * Obtain the unused portion of the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of obtaining a total remaining or
     * available capacity measurement; for those that do not, this property is likely to yield 0, or
     * the property may be expensive to calculate.
     *
     * It is possible that capacitiy, bytesUsed, and bytesFree all provide information, yet the
     * capacity value does not match the sum of the bytesUsed and bytesFree values. This value is
     * considered to be _advisory_, and not definitive.
     */
    @RO Int bytesFree;
    }
