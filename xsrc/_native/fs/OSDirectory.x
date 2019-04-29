/**
 * Native OS Directory implementation.
 */
class OSDirectory
        implements fs.Directory
    {
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
