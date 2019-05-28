import Ecstasy.fs.File;
import Ecstasy.fs.FileChannel;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Native OS File implementation.
 */
const OSFile
        extends OSFileNode
        implements File
    {
    @Override
    File truncate(Int newSize = 0);

    @Override
    conditional FileStore openArchive()
        {
        return False; // TODO
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[Write]);

    // ----- native --------------------------------------------------------------------------------

    @Override
    @Abstract immutable Byte[] contents;

    @Override
    @Abstract Int size;
    }
