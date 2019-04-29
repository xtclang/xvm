/**
 * Native OS File implementation.
 */
class OSFile
        implements fs.File
    {
    @Override
    immutable Byte[] contents;

    @Override
    File truncate(Int newSize = 0);

    @Override
    conditional File link()
        {
        TODO
        }

    @Override
    conditional FileStore archive()
        {
        TODO
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[WriteOption.Write]);
    }
