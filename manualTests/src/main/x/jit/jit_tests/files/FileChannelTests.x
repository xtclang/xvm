import ecstasy.fs.FileChannel;
import ecstasy.io.ReadBuffer;

/**
 * Tests for FileChannel.
 */
class FileChannelTests {

    static Byte[] NoBytes = [];

    @Inject Directory tmpDir;

    void run() {
        // clean up from any previous tests that may have failed
        cleanup();

        shouldOpenFileChannel();
        cleanup();
        shouldCloseFileChannel();
        cleanup();
        shouldOpenReadOnlyFileChannel();
        cleanup();
        shouldOpenWriteOnlyFileChannel();
        cleanup();
        shouldReadFileChannel();
        cleanup();
    }

    void shouldOpenFileChannel() {
        Directory   dir     = tmpDir.dirFor("foo").ensure();
        File        file    = dir.fileFor("foo.txt").ensure();
        Byte[]      bytes   = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
        file.contents = bytes;

        using (FileChannel channel = file.open()) {
            assert channel.size == file.size;
            assert channel.position == 0;
            assert channel.remaining == file.size;
            assert channel.readable;
            assert channel.writable;
            assert !channel.eof;
        }
    }

    void shouldCloseFileChannel() {
        Directory   dir     = tmpDir.dirFor("foo").ensure();
        File        file    = dir.fileFor("foo.txt").ensure();
        Byte[]      bytes   = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
        file.contents = bytes;

        FileChannel channel = file.open();
        channel.close();
        assert !channel.readable;
        assert !channel.writable;
    }

    void shouldOpenReadOnlyFileChannel() {
        Directory   dir     = tmpDir.dirFor("foo").ensure();
        File        file    = dir.fileFor("foo.txt").ensure();

        using (FileChannel channel = file.open(Read, [])) {
            assert channel.readable;
            assert !channel.writable;
        }
    }

    void shouldOpenWriteOnlyFileChannel() {
        Directory   dir     = tmpDir.dirFor("foo").ensure();
        File        file    = dir.fileFor("foo.txt").ensure();

        using (FileChannel channel = file.open(NoRead, [Write])) {
            assert !channel.readable;
            assert channel.writable;
        }
    }

    void shouldReadFileChannel() {
        Directory   dir     = tmpDir.dirFor("foo").ensure();
        File        file    = dir.fileFor("foo.txt").ensure();
        Byte[]      bytes   = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
        file.contents = bytes;

// TODO requires @Future support
//        using (FileChannel channel = file.open()) {
//            ReadBuffer? buffer = channel.read();
//            assert buffer.is(ReadBuffer);
//        }
    }

    void cleanup() {
        tmpDir.dirFor("foo").deleteRecursively();
    }
}
