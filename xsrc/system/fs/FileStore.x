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
     * Specifies whether this FileStorage is known to be explicitly read-only.
     */
    @RO Boolean readOnly;

    /**
     * Obtain the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of obtaining a total capacity
     * measurement. There exist scenarios in which the capacity is unknowable (or terribly
     * inefficient to calculate), and thus this information is only conditionally available.
     */
    conditional Int capacityBytes();

    /**
     * Obtain the used portion of the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of determining a total number of
     * used bytes with near-real-time accuracy. However, some file systems are incapable of
     * efficiently determining aggregate usage statistics, and thus this information is only
     * conditionally available.
     */
    conditional Int usedBytes();

    /**
     * Obtain the unused portion of the storage capacity in bytes, if it is knowable.
     *
     * Most devices and file systems provide an efficient means of determining a total number of
     * used bytes with near-real-time accuracy. However, some file systems are incapable of
     * efficiently determining aggregate usage statistics, and thus this information is only
     * conditionally available.
     */
    conditional Int unusedBytes();

    /**
     * The root directory of the FileStore. This is conceptually analogous to "/" in UNIX, for
     * example. However, a FileStore is an abstraction, so its root may not correspond to the the
     * local file system root, and it may not even correspond to the file system at all.
     */
    @RO Directory root;

    /**
     * Obtain the directory or file at the specified path.
     *
     * @param path   the Path of the Directory or File to find
     *
     * @return the Directory or File at that path, only if is exists
     */
    conditional Directory|File find(Path path);

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
    }
