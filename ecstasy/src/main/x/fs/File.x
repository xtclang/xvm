/**
 * File represents a file in a FileStore.
 */
interface File
        extends FileNode
    {
    /**
     * The contents of the file, as an Array of Byte. If the File is writable, then the contents
     * can be set.
     *
     * This property potentially hides a great deal of complexity for reading from and writing to
     * files, by making the contents appear to be synchronously available, in-memory, as a simple
     * array of bytes; however, using this property will be quite inefficient when working with
     * large files.
     *
     * @throws FileNotFound  if the file does not exist
     * @throws AccessDenied  if the necessary file permissions to get or set the property have not
     *                       been granted
     */
    immutable Byte[] contents;

    /**
     * Modify the contents of the file so that it has the specified size.
     *
     * @param newSize  the size to truncate the file to; defaults to 0
     *
     * @return this File
     *
     * @throws FileNotFound  if the file does not exist
     * @throws AccessDenied  if permission to modify the file has not been granted
     */
    File truncate(Int newSize = 0);

    /**
     * Determine if this file is an _archive_, which is a directory structure encoded into a file,
     * and which may contain any number of directories and files nested within it. If the file is
     * an archive **and** the archive format is known and supported, then this method allows a
     * caller to obtain a [FileStore] reference representing the contents of the archive.
     *
     * Common archive formats include: .zip, .tar, .gz, .dmg.
     */
    conditional FileStore openArchive();

    enum ReadOption
        {
        /**
         * Disallow read access.
         *
         * Incompatible with all other read options.
         */
        NoRead,

        /**
         * Allow read access.
         *
         * Implied by all following read options.
         */
        Read,

        /**
         * As long as the file is open, prevent other operations that require read access to the
         * file, to the extent supported by the file system.
         */
        Exclusive
        }

    enum WriteOption
        {
        /**
         * Disallow write access.
         *
         * Incompatible with all other write options.
         */
        NoWrite,

        /**
         * Allow write access.
         *
         * Implied by all following write options.
         */
        Write,

        /**
         * As part of opening the file to write to it, create a new file if the file does not exist.
         *
         * Incompatible with Create and any option that implies Create.
         */
        Ensure,

        /**
         * Create a new file, failing if the file already exists. (Atomic operation.)
         */
        Create,

        /**
         * Indicate that the file will be mostly filled with zero bytes.
         *
         * This option implies the Create option, so the open will fail if the file already exists.
         *
         * The file system may not provide support for sparse files; in that case, the file may use
         * more actual storage than initially anticipated.
         */
        Sparse,

        /**
         * Create a temporary file that is deleted when it is closed.
         *
         * This option implies the Create option, so the open will fail if the file already exists.
         *
         * If the file system does not natively support this option, then the file deletion on
         * close or exit is not strictly guaranteed, but a "best effort" will be made.
         */
        Temp,

        /**
         * As part of opening the file to write to it, truncate the file length to 0.
         *
         * Incompatible with Create and any option that implies Create.
         */
        Truncate,

        /**
         * Indicate that all writes should occur at the end of the file.
         */
        Append,

        /**
         * As long as the file is open, prevent other operations that require write access to the
         * file, to the extent supported by the file system.
         */
        Exclusive,

        /**
         * Force disk sync of data on every write.
         *
         * This is likely to be expensive from an efficiency standpoint.
         */
        SyncData,

        /**
         * Force disk sync on data *and meta-data* on every write.
         *
         * This is likely to be very expensive from an efficiency standpoint.
         */
        SyncAll
        }

    /**
     * Open the file, creating a channel.
     *
     * @param read   the ReadOption to use when opening the channel; defaults to Read
     * @param write  the WriteOption to use when opening the channel; defaults to Write
     *
     * @throws FileNotFound       if the file does not exist, and neither Create nor Ensure are
     *                            specified or implied
     *                            file to exist
     * @throws AccessDenied       if the necessary permissions to read from and/or write to the
     *                            file, as implied by the specified options, have not been granted
     * @throws FileAlreadyExists  if the file exists, but the WriteOption of Create is specified or
     *                            implied
     */
    FileChannel open(ReadOption read=Read, WriteOption[] write = [Write]);

    @Override
    Appender<Char> emitListing(Appender<Char> buf, Boolean recursive = False, String indent = "")
        {
        return buf.addAll(indent)
           .addAll(name)
           .add('\n');
        }
    }
