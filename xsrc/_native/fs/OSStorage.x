import Ecstasy.fs.Directory;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Native OSStorage service.
 */
service OSStorage
    {
    construct()
        {
        fileStore = new OSFileStore(false);
        }

    FileStore fileStore;

    Directory rootDir.get()
        {
        return fileStore.root;
        }

    Directory directoryFor(String pathString)
        {
        // natural code here to create Path from String
        // get FileStore and look up the path and return it
        TODO
        }
    }
