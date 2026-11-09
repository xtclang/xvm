package org.xtclang._native.fs;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.Set;

import org.xtclang._native.io.RawChannel;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;
import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import static org.xtclang.ecstasy.Exception.$io;

/**
 * The native implementation of {@code _native.fsRawOSFileChannel}.
 */
public class RawOSFileChannel extends RawChannel {

    private final FileChannel $channel;

    private final java.nio.file.Path $path;

    private final boolean $readable;

    private final boolean $writable;

    protected RawOSFileChannel(Ctx ctx, FileChannel channel, Path path, Set<OpenOption> openOpts) {
        super(ctx);
        $channel  = channel;
        $path     = path;
        $readable = openOpts.contains(StandardOpenOption.READ);
        $writable = openOpts.contains(StandardOpenOption.WRITE);
    }

    @Override
    public long $getPreferredBufferSize(Ctx ctx) {
        long size = super.$getPreferredBufferSize(ctx);
        try {
            return Math.clamp($channel.size(), 1024L, size);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property getter for {@code Int size;}.
     */
    public long size$get$p(Ctx ctx) {
        try {
            return $channel.size();
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property setter for {@code Int size;}.
     */
    public void size$set$p(Ctx ctx, long size) {
        try {
            $channel.truncate(size);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property getter for {@code Int position;}.
     */
    public long position$get$p(Ctx ctx) {
        try {
            return $channel.position();
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property setter for {@code Int position;}.
     */
    public void position$set$p(Ctx ctx, long position) {
        try {
            $channel.position(position);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property getter for {@code Boolean readable;}.
     */
    public boolean readable$get$p(Ctx ctx) {
        return $readable;
    }

    /**
     * The primitive property getter for {@code Boolean writable;}.
     */
    public boolean writable$get$p(Ctx ctx) {
        return $writable;
    }

    /**
     * The primitive property getter for {@code Boolean closed;}.
     */
    @Override
    public boolean closed$get$p(Ctx ctx) {
        return !$channel.isOpen();
    }

    /**
     * The native implementation of {@code void close()}.
     */
    @Override
    public void close(Ctx ctx) {
        try {
            $channel.close();
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of {@code void flush()}.
     */
    public void flush(Ctx ctx) {
        try {
            $channel.force(false);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of {@code Byte[]|Int take()}.
     */
    public Object take(Ctx ctx) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int read = $channel.read(buffer);
            if (read < 0) {
                return Int64.$box(-1L);
            }
            return ArrayᐸUInt8ᐳ.$makeArray(ctx, read, buffer);
        } catch (NonReadableChannelException e) {
            return Int64.$box(-2L); // InputShutdown
        } catch (ClosedChannelException e) {
            return Int64.$box(-3L); // closed
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive implementation of {@code Int submit(Byte[] buffer, Int start, Int end)}.
     */
    public long submit$p(Ctx ctx, ArrayᐸUInt8ᐳ buffer, long start, long end) {
        try {
            return $channel.write(buffer.$getBuffer(ctx, start, end));
        } catch (NonReadableChannelException e) {
            return -1L; // InputShutdown
        } catch (ClosedChannelException e) {
            return -2L; // closed
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }
}
