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
     * @param path  the Path of the Directory or File to find
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

    /**
     * Copy the contents specified by the source path to the destination path.
     *
     * * If the source path specifies a non-existent file-node, then a FileNotFound is thrown.
     * * If the destination path specifies an existent file, then a FileAlreadyExists is thrown.
     * * If the source path specifies a file and the destination path specifies an existent
     *   directory, then a FileAlreadyExists is thrown. (This behavior is notably different from
     *   most command-line "copy" commands, which would automatically copy the file _under_ the
     *   destination path; this method, on the other hand, is specified such that the resulting
     *   file *always* has the same exact path as specified in the destination path parameter.)
     * * If the source path specifies a file and the destination path specifies a non-existent
     *   file-node, then a copy of the file is created such that the destination path refers to the
     *   newly copied file. If the parent directory of the destination path did not exist, then it
     *   is automatically created (recursively, as necessary) as part of the copy process.
     * * If the source path specifies a directory, and the destination path specifies an existent
     *   directory, then an analysis of the result of the copy is performed (before copying any
     *   contents), and a determination is made whether the result of the copy would modify the
     *   contents of any *file* in the destination directory; if the copy would alter any existent
     *   *file*, or if any directory/file mismatch exists between the source and the destination,
     *   then a FileAlreadyExists exception is thrown.
     * * Otherwise, if the source path specifies a directory, then all of the contents of the source
     *   directory are copied to the destination directory, maintaining the same hierarchical
     *   structure. If the parent directory of the destination path did not exist, then it is
     *   automatically created (recursively, as necessary) as part of the copy process. The
     *   implementation may choose _not_ to copy files that have an identical copy of the file
     *   already in the corresponding destination location.
     *
     * This operation does not alter the source.
     *
     * @return the File or Directory for the new copy; note that the path of the new File or
     *         Directory will always be the exact specified destination path
     *
     * @throws FileNotFound       if the source path does not exist
     * @throws AccessDenied       if access to read from the source path is denied, or access to
     *                            write to the destination path is denied
     * @throws FileAlreadyExists  if a file or directory exists at (or under) the destination path
     *                            in a manner that prevents a successful copy from being performed
     */
    Directory|File copy(Path source, Path dest);

    /**
     * Move the contents specified by the source path to the destination path. This operation is
     * likely to be dramatically more efficient than first copying and then subsequently deleting
     * the source, since most file systems can perform a move operation in a manner similar to a
     * file renaming operation.
     *
     * * If the source path specifies a non-existent file-node, then a FileNotFound is thrown.
     * * If the destination path specifies an existent file, then a FileAlreadyExists is thrown.
     * * If the source path specifies a file and the destination path specifies an existent
     *   directory, then a FileAlreadyExists is thrown. (This behavior is notably different from
     *   most command-line "move" commands, which would automatically move the file _under_ the
     *   destination path; this method, on the other hand, is specified such that the resulting
     *   file *always* has the same exact path as specified in the destination path parameter.)
     * * If the source path specifies a file and the destination path specifies a non-existent
     *   file-node, then the file is moved such that the destination path refers to the resulting
     *   file. If the parent directory of the destination path did not exist, then it is
     *   automatically created (recursively, as necessary) as part of the move process.
     * * If the source path specifies a directory, and the destination path specifies an existent
     *   directory, then an analysis of the result of the move is performed (before moving any
     *   contents), and a determination is made whether the result of the move would modify the
     *   contents of any *file* in the destination directory; if the move would alter any existent
     *   *file*, or if any directory/file mismatch exists between the source and the destination,
     *   then a FileAlreadyExists exception is thrown.
     * * Otherwise, if the source path specifies a directory, then all of the contents of the source
     *   directory are moved to the destination directory, maintaining the same hierarchical
     *   structure. If the parent directory of the destination path did not exist, then it is
     *   automatically created (recursively, as necessary) as part of the move process. The
     *   implementation may choose _not_ to move files that have an identical copy of the file
     *   already in the corresponding destination location.
     *
     * At the successful conclusion of this operation, the specified source path will no longer
     * exist.
     *
     * @return the File or Directory for the new copy; note that the path of the new File or
     *         Directory will always be the exact specified destination path
     *
     * @throws FileNotFound       if the source path does not exist
     * @throws AccessDenied       if access to read from the source path is denied, or access to
     *                            write to the destination path is denied
     * @throws FileAlreadyExists  if a file or directory exists at (or under) the destination path
     *                            in a manner that prevents a successful move from being performed
     */
    Directory|File move(Path source, Path dest);

    typedef function void () Cancellable;

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
     * It is possible that capacity, bytesUsed, and bytesFree all provide information, yet the
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
     * It is possible that capacity, bytesUsed, and bytesFree all provide information, yet the
     * capacity value does not match the sum of the bytesUsed and bytesFree values. This value is
     * considered to be _advisory_, and not definitive.
     */
    @RO Int bytesFree;

    /**
     * Emit a directory-style hierarchical listing to the specified Appender.
     *
     * @param buf        the Appender to emit the listing to
     * @param recursive  True to have the listing recurse through sub-directories
     */
    Appender<Char> emitListing(Appender<Char> buf, Boolean recursive = True)
        {
        buf.addAll("FileStore:\n");
        return root.emitListing(buf, recursive, "");
        }
    }
