/**
 * Directory represents a directory in a FileStore.
 */
interface Directory
    {
    /**
     *
     */
    Iterator<String> names();

    Iterator<Directory> dirs();

    Iterator<File> files();

    // TODO other ways to "visit" including recursive

    conditional Directory|File find(String name);

    Directory assumeDir(String name);

    conditional Directory createDir(String name);

    Boolean deleteRecursively();

    File assumeFile(String name);

    conditional File createFile(String sName);

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
