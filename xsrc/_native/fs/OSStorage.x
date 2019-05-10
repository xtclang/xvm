import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Native OSStorage service.
 */
service OSStorage
    {
    construct()
        {
        }
    finally
        {
        fileStore = new OSFileStore(this, false);
        }

    @Unassigned
    FileStore fileStore;

    Directory rootDir.get()
        {
        return fileStore.root;
        }

    // ----- native --------------------------------------------------------------------------------

    conditional Directory|File find(String pathString);

    Directory directoryFor(String pathString);
    }
