/**
 * FileStore represents an abstract storage device.
 */
interface FileStore
    {
    /**
     * The store name. This is usually the name that identified this FileStore injection within
     * the enclosing container.
     */
    @RO String name;

    /**
     * Specifies whether or not this storage is read-only.
     */
    @RO Boolean readOnly;

    /**
     * The storage size in bytes.
     */
    @RO Int totalSpace;

    /**
     * The number of bytes available on this storage.
     */
    @RO Int availableSpace;

    /**
     * Check whether the specified Path is associated with a file or directory.
     */
    Boolean exists(Path path);

    /**
     * Check whether the file denoted by the specified Path is a directory.
     */
    Boolean isDirectory(Path path);

    /**
     * Check whether the file denoted by the specified Path is a regular file.
     */
    Boolean isFile(Path path);

    /**
     * Retrieve the last modified time for the file or directory denoted by the specified Path.
     */
    Time getLastModified(Path path);

    /**
     * Check whether the file or directory denoted by the specified Path is readable.
     *
     * If there is no file denoted by this path, the answer indicates a possibility of
     * creation of a readable file at this location.
     */
    Boolean canRead(Path path);

    /**
     * Check whether the file or directory denoted by the specified Path is writable.
     *
     * If there is no file denoted by this path, the answer indicates a possibility of
     * creation of a writable file at this location.
     */
    Boolean canWrite(Path path);

    /**
     * Return an array of absolute Paths for files and directories in the directory denoted by the
     * specified Path.
     *
     * If the specified Path is not a directory, then this method returns `false`.
     */
    conditional Path[] list(Path path);

    /**
     * Create the directory denoted by the specified Path.
     *
     * @throws IOException if the directory could not be created
     */
    void makeDir(Path path);

    /**
     * Remove a file or directory denoted by the specified Path.
     *
     * @throws IOException if the file or directory could not be removed
     */
    void remove(Path path);

    /**
     * Rename the file denoted by source path to the destination.
     *
     * @throws IOException if the operation has failed
     */
    void rename(Path sourcePath, Path newPath);

    /**
     * Copy the file denoted by source path to the destination.
     *
     * @throws IOException if the operation has failed
     */
    void copy(Path sourcePath, Path newPath);

    /**
     * Updates the last-modified time of the file denoted by the specified Path.
     *
     * @return false if the file doesn't exist or is not writable
     */
    Boolean touch(Path path);

    /**
     * Open a FileChannel.
     */
    FileChannel open(Path path); // TODO: Options, Attributes
    }
