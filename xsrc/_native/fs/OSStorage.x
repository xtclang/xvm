import Ecstasy.fs.Directory;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Native OS FileStore implementation.
 */
service OSStorage
    {
    construct()
        {
        }

    FileStore fileStore;

    Directory directoryFor(String )
        {
        // natural code here to create Path from String
        // get FileStore and look up the path and return it
        }
    }
