/**
 * Directory represents a directory in a FileStore.
 */
interface Directory
        extends FileNode
    {
    /**
     * Obtain an iterator of all of the names that exist immediately under this directory, including
     * names of both files and directories.
     */
    Iterator<String> names();

    /**
     * Obtain an iterator of all of the directories that exist immediately under this directory.
     */
    Iterator<Directory> dirs();

    /**
     * Obtain an iterator of all of the files that exist immediately under this directory.
     */
    Iterator<File> files();

    /**
     * Obtain an iterator of all of the files that exist within this directory, including those
     * that exist immediately under this directory, and also those contained in sub-directories
     * (at any level) under this directory.
     */
    Iterator<File> filesRecursively();

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
    Directory dirFor(String name);

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
     * @return this directory iff it did exist, and now does not
     *
     * @throws AccessDenied  if permission to delete the file has not been granted
     */
    Boolean deleteRecursively();

    /**
     * Watch this directory and everything nested under directories under this directory, and report
     * any events related to any such directory.
     *
     * @param watcher  the FileWatcher to invoke when anything in or under this directory has a
     *                 watchable event
     *
     * @return a Cancellable object that allows the caller to cancel the watch
     */
    Cancellable watchRecursively(FileWatcher watcher);

    @Override
    Appender<Char> emitListing(Appender<Char> buf, Boolean recursive = False, String indent = "")
        {
        Boolean root = indent == "";

//        created.append(buf);
//        buf.addAll("  ");
//        modified.append(buf);
//        buf.addAll("  ");
//        String bytes = size.toString()

        if (recursive || !root)
            {
            buf.addAll(indent)
               .addAll(name)
               .add('/')
               .add('\n');
            }

        if (recursive || root)
            {
            String nextIndent = root
                    ? "  +- "
                    : "  |  " + indent;

            for (Directory dir : dirs())
                {
                dir.emitListing(buf, recursive, nextIndent);
                }

            for (File file : files())
                {
                file.emitListing(buf, recursive, nextIndent);
                }
            }

        return buf;
        }
    }
