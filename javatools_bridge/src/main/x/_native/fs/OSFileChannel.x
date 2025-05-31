import ecstasy.fs.FileChannel;

import io.RTChannel;

/**
 * Native OS FileChannel implementation.
 */
service OSFileChannel
        extends RTChannel(rawChannel)
        implements FileChannel {

    construct(RawOSFileChannel rawChannel) {
        construct RTChannel(rawChannel);
    }

    @Override
    protected RawOSFileChannel rawChannel.get() = super().as(RawOSFileChannel);

    // ----- FileChannel API -----------------------------------------------------------------------

    @Override
    Int size {
        Int get() = rawChannel.size;
        void set(Int newSize) {
            rawChannel.size = newSize;
        }
    }

    @Override
    Int position {
        Int get() = rawChannel.position;
        void set(Int newPosition) {
            rawChannel.position = newPosition;
        }
    }

    @Override
    Boolean eof.get() = rawChannel.eof;

    @Override
    void flush() = rawChannel.flush();

    @Override
    String toString() = "FileChannel";
}