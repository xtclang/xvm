/**
 * Native OS Directory implementation.
 */
class OSDirectory
        implements fs.Directory
    {
    @RO Path path
    @RO String name
    @RO Boolean exists
    @RO DateTime created
    DateTime modified
    @RO DateTime accessed
    @RO Boolean readable
    @RO Boolean writable
    conditional FileNode create()
    FileNode ensure()
    conditional FileNode delete()
    conditional FileNode renameTo(String name)
    @RO Int size
    Cancellable watch(FileWatcher watch)

    Iterator<String> names()
    Iterator<Directory> dirs()
    Iterator<File> files()
    Iterator<File> filesRecursively()
    conditional Directory|File find(String name)
    Directory dirFor(String name)
    File fileFor(String name)
    conditional Directory deleteRecursively()
    Cancellable watchRecursively(FileWatcher watch)
    }
