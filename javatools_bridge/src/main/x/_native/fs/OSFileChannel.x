import ecstasy.fs.FileChannel;

import io.RawChannel;
import io.RTChannel;

/**
 * Native OS FileChannel implementation.
 */
service OSFileChannel(RawChannel rawChannel)
        extends RTChannel(rawChannel)
        implements FileChannel
    {
    // ----- FileChannel API -----------------------------------------------------------------------

    @Override
    Int size.get()
        {TODO("native");}

    @Override
    Int position.get()
        {TODO("native");}

    @Override
    void flush()
        {TODO("native");}
    }
