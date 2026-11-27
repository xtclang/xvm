package org.xtclang._native.fs;

import java.io.IOException;
import java.io.OutputStream;

import java.nio.ByteBuffer;

import java.nio.channels.FileChannel;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.HashSet;
import java.util.Set;

import org.xtclang.ecstasy.RangeᐸInt64ᐳ;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;
import org.xtclang.ecstasy.collections.ArrayᐸUInt8ᐳ;

import org.xtclang.ecstasy.fs.File;

import org.xvm.javajit.Ctx;

import org.xvm.util.Files;

import static java.nio.file.Files.newOutputStream;

import static org.xtclang.ecstasy.Exception.$fileNotFound;
import static org.xtclang.ecstasy.Exception.$io;
import static org.xtclang.ecstasy.Exception.$oob;
import static org.xtclang.ecstasy.Exception.$unsupported;

import static org.xvm.util.Files.NO_ACCESS;
import static org.xvm.util.Files.READ_ONLY;
import static org.xvm.util.Files.READ_WRITE;
import static org.xvm.util.Files.WRITE_ONLY;

/**
 * Native implementation for {@code _native.fs.OSFile}.
 */
public class OSFile
        extends OSFileNode
        implements File {

    public OSFile(Ctx ctx, OSFileStore store, Path path) {
        super(ctx, store, path);
    }

    /**
     * The primitive property getter for {@code Int size.get()}.
     */
    public long size$get$p(Ctx ctx) {
        try {
            return Files.sizeIfExists($getJavaPath(ctx));
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native property getter for {@code immutable Byte[] contents;}.
     */
    public ArrayᐸUInt8ᐳ contents$get(Ctx ctx) {
        Path path = $getJavaPath(ctx);
        try {
            java.io.File file = path.toFile();
            if (!file.exists()) {
                throw $fileNotFound(ctx, path$get(ctx));
            }
            return ArrayᐸUInt8ᐳ.$makeArray(ctx, Files.readFileBytes(file));
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native property setter for {@code immutable Byte[] contents;}.
     */
    public void contents$set(Ctx ctx, ArrayᐸUInt8ᐳ data) {
        Path         path = $getJavaPath(ctx);
        OpenOption[] opts = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
                             StandardOpenOption.CREATE};

        try (OutputStream out = newOutputStream(path, opts)) {
            long size = data.size$get$p(ctx);
            for (int i = 0; i < size; i++) {
                out.write(data.$getByte(i));
            }
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of {@code immutable Byte[] readImpl(Range<Int> range)}.
     */
    public ArrayᐸUInt8ᐳ readImpl(Ctx ctx, RangeᐸInt64ᐳ range) {
        Path         path = $getJavaPath(ctx);
        java.io.File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw $fileNotFound(ctx, path$get(ctx));
        }

        long start = range.effectiveLowerBound$get$p(ctx);
        long end   = range.effectiveUpperBound$get$p(ctx) + 1L;
        long size  = file.length();

        if (start < 0L) {
            throw $oob(ctx, "Negative index " + start);
        }
        if (end > size) {
            throw $oob(ctx, end, 0L, size);
        }

        // TODO at some point we need to handle reading larger than Integer.MAX_VALUE bytes as
        //  the Ecstasy APIs would suggest that is supported
        int        capacity = (int) (end - start);
        ByteBuffer buffer   = ByteBuffer.allocate(capacity);
        try {
            int bytesRead = Files.readAt(path, buffer, start);
            // TODO if bytesRead is not the expected size should we throw OOB as presumably the
            //  file length changed after the length check?
            return ArrayᐸUInt8ᐳ.$makeArray(ctx, bytesRead, buffer);
        } catch (Exception e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive implementation of {@code void truncateImpl(Int newSize)}.
     */
    public void truncateImpl$p(Ctx ctx, long newSize) {
        Path         path = $getJavaPath(ctx);
        java.io.File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw $fileNotFound(ctx, path$get(ctx));
        }

        long length = file.length();
        if (newSize < 0) {
            newSize += length;
        }

        if (newSize > length || newSize < 0) {
            throw $oob(ctx, newSize, 0, length);
        }

        try {
            Files.truncate(path, newSize);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of {@code void appendBytes(Byte[] contents)}.
     */
    public void appendBytes(Ctx ctx, ArrayᐸUInt8ᐳ contents) {
        Path path = $getJavaPath(ctx);
        try (OutputStream out = Files.openAppendStream(path)) {
            long size = contents.size$get$p(ctx);
            for (int i = 0; i < size; i++) {
                out.write(contents.getElement$pi(ctx, i));
            }
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of {@code void appendFile(OSFileNode file)}.
     */
    public void appendFile(Ctx ctx, OSFileNode file) {
        Path srcPath  = file.$getJavaPath(ctx);
        Path destPath = $getJavaPath(ctx);
        try {
            Files.appendFile(srcPath, destPath);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The native implementation of
     * {@code RawOSFileChannel openImpl(ReadOption read, WriteOption[] write)}.
     */
    public RawOSFileChannel openImpl(Ctx ctx, File.ReadOption read, ArrayᐸObjectᐳ write) {
        if (read == null) {
            read = ReadOption.Read.$INSTANCE;
        }

        Set<OpenOption> openOpts    = NO_ACCESS;
        long            readOrdinal = read.$ordinal;

        // the most common two cases are Read/Write and Read/NoWrite
        if (write == null) {
            // the default is "Read/Write"
            if (readOrdinal == ReadOption.NoRead.$INSTANCE.$ordinal) {
                openOpts = WRITE_ONLY;
            } else if (readOrdinal == ReadOption.Read.$INSTANCE.$ordinal) {
                openOpts = READ_WRITE;
            } else if (readOrdinal == ReadOption.Exclusive.$INSTANCE.$ordinal) {
                throw $unsupported(ctx, "ReadOption.Exclusive");
            }
        } else {
            if (write.empty$get$p(ctx)) {
                if (readOrdinal == ReadOption.Read.$INSTANCE.$ordinal) {
                    openOpts = READ_ONLY;
                } else if (readOrdinal == ReadOption.Exclusive.$INSTANCE.$ordinal) {
                    throw $unsupported(ctx, "ReadOption.Exclusive");
                }
            } else {
                openOpts = new HashSet<>();
                if (readOrdinal == ReadOption.Read.$INSTANCE.$ordinal) {
                    openOpts.add(StandardOpenOption.READ);
                } else if (readOrdinal == ReadOption.Exclusive.$INSTANCE.$ordinal) {
                    throw $unsupported(ctx, "ReadOption.Exclusive");
                }

                openOpts.add(StandardOpenOption.WRITE);

                long count = write.size$get$p(ctx);
                for (int i = 0; i < count; i++) {
                    WriteOption writeOpt = (WriteOption) write.getElement$p(ctx, i);
                    OpenOption  openOpt  = writeOpt.$opt;
                    if (openOpt == null) {
                        throw $unsupported(ctx, "WriteOption." + writeOpt.$name);
                    }
                    openOpts.add(openOpt);
                }
            }
        }

        Path path = $getJavaPath(ctx);
        try {
            FileChannel channel = FileChannel.open(path, openOpts);
            return new RawOSFileChannel(ctx, channel, openOpts);
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }
}
