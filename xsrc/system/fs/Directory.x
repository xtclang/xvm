/**
 * Directory represents a directory in a FileStore.
 */
interface Directory
        extends FileNode
    {
    /**
     *
     */
    Iterator<String> names();

    Iterator<Directory> dirs();

    Iterator<File> files();

    Iterator<File> filesRecursively();

    typedef function void (File) FileVisitor;

    void visitFiles(FileVisitor visit);
    // TODO other ways to "visit" including directories, all, recursive

    /**
     * Obtain the Directory or File for the specified name.
     *
     * @param name  the name of the Directory or File to find
     *
     * @return the Directory or File with that name, only if is exists
     */
    conditional Directory|File find(String name);

    /**
     * Obtain a Directory object for the specified path, whether or not the directory actually
     * exists. This method allows the caller to obtain a Directory object that can be watched,
     * created, etc., even if the directory did not previously exist.
     *
     * If a file exists at the location specified by the path, then it will still be possible to
     * obtain a Directory object for the path, but the Directory will not exist, and it will not
     * be able to be created without first deleting the file.
     */
    Directory directoryFor(String name);

    /**
     * Obtain a File object for the specified path, whether or not the file actually exists.
     * This method allows the caller to obtain a File object that can be watched, created, etc.,
     * even if the file did not previously exist.
     *
     * If a directory exists at the location specified by the path, then it will still be possible
     * to obtain a File object for the path, but the file will not exist, and it will not be able
     * to be created without first deleting the directory.
     */
    File fileFor(String name);

    /**
     * Delete the contents of this directory, if any, and then delete this directory, if it exists.
     *
     * @return True iff the directory did exist, and now does not
     */
    Boolean deleteRecursively(); // TODO conditional FileNode ??

    typedef function void () Cancellable;

    /**
     * Watch this directory and everything nested under directories under this directory, and report
     * any events related to any such directory.
     *
     * @param watch  the FileWatcher to invoke when anything in or under this directory has a
     *               watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watchRecursively(FileWatcher watch);
    }
