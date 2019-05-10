import Ecstasy.fs.File;
import Ecstasy.fs.FileChannel;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Native OS File implementation.
 */
class OSFile
        extends OSFileNode
        implements File
    {
    @Override
    File truncate(Int newSize = 0);

    @Override
    conditional File link()
        {
        return False; // TODO
        }

    @Override
    conditional FileStore archive()
        {
        return False; // TODO
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[Write]);

    // ----- native --------------------------------------------------------------------------------

    @Override
    immutable Byte[] contents;
    }
