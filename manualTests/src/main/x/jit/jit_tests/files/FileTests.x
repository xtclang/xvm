import ecstasy.fs.FileNotFound;
import ecstasy.fs.Path;

/**
 * Tests for File.
 */
class FileTests {

    static Byte[] NoBytes = [];

    @Inject Directory tmpDir;

    void run() {
        // clean up from any previous tests that may have failed
        cleanup();

        shouldHaveFileContent();
        cleanup();
        shouldThrowGettingFileContentsForNonExistentFile();
        cleanup();
        shouldThrowReadingNonExistentFile();
        cleanup();
        shouldReadWholeFile();
        cleanup();
        shouldReadFileSubRange();
        cleanup();
        shouldReadZeroLengthRange();
        cleanup();
        shouldThrowForNegativeReadRange();
        cleanup();
        shouldThrowForTooBigReadRange();
        cleanup();
        shouldThrowTruncatingNonExistentFile();
        cleanup();
        shouldTruncateFileToZero();
        cleanup();
        shouldTruncateFileToZeroWithNegativeSize();
        cleanup();
        shouldTruncateFileUsingPositiveSize();
        cleanup();
        shouldTruncateFileUsingNegativeSize();
        cleanup();
        shouldThrowTruncatingToTooBigSize();
        cleanup();
        shouldThrowTruncatingToNegativeSize();
        cleanup();
        shouldAppendBytesToExistingFile();
        cleanup();
        shouldAppendZeroBytesToExistingFile();
        cleanup();
        shouldAppendZeroBytesToNonExistentFile();
        cleanup();
        shouldAppendFileToExistingFile();
        cleanup();
        shouldAppendFileToNonExistentFile();
        cleanup();
        shouldAppendEmptyFileToExistingFile();
        cleanup();
        shouldAppendEmptyFileToNonExistentFile();
        cleanup();

    }

    void shouldHaveFileContent() {
        Directory dir  = tmpDir.dirFor("foo").ensure();
        File      file = dir.fileFor("foo.txt").ensure();

        Byte[] bytes = [1, 2, 3, 4];
        file.contents = bytes;
        assert file.exists;
        assert file.size == bytes.size;
        // TODO array equals does not work in the JIT
        // assert file.contents == bytes;
        Byte[] contents = file.contents;
        assert contents[0] == 1;
        assert contents[1] == 2;
        assert contents[2] == 3;
        assert contents[3] == 4;

        file.contents = NoBytes;
        assert file.exists;
        assert file.size == 0;
        // TODO array equals does not work in the JIT
        // assert file.contents == NoBytes;
    }

    void shouldThrowGettingFileContentsForNonExistentFile() {
        Directory dir  = tmpDir.dirFor("foo").ensure();
        File      file = dir.fileFor("foo.txt");
        assert !file.exists;

        try {
            Byte[] contents = file.contents;
            assert as "Should have thrown FileNotFound for missing file";
        } catch (FileNotFound e) {
            assert e.message == file.path.toString();
        } catch (ecstasy.fs.PathException e) {
            // TODO when running in the interpreter we get a PathException with a slightly different
            // message. The JIT is correct.
            assert e.message == "FileNotFound: " + file.path.toString();
        }
    }

    void shouldThrowReadingNonExistentFile() {
        Directory dir  = tmpDir.dirFor("foo").ensure();
        File      file = dir.fileFor("foo.txt");
        assert !file.exists;

        try {
            file.read(1..10);
            assert as "Should have thrown FileNotFound for missing file";
        } catch (FileNotFound e) {
            assert e.message == file.path.toString();
        } catch (ecstasy.fs.PathException e) {
            // TODO when running in the interpreter we get a PathException with a slightly different
            // message. The JIT is correct.
            assert e.message == "FileNotFound: " + file.path.toString();
        }
    }

    void shouldReadWholeFile() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        Byte[] buf = file.read(0..<bytes.size);
        assert buf.size == bytes.size;
        // TODO array equals does not work in the JIT
        // assert buf == bytes;
        assert buf[0] == 1;
        assert buf[1] == 2;
        assert buf[2] == 3;
        assert buf[3] == 4;
        assert buf[4] == 5;
        assert buf[5] == 6;
        assert buf[6] == 7;
        assert buf[7] == 8;
        assert buf[8] == 9;
        assert buf[9] == 0;
    }

    void shouldReadFileSubRange() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        Byte[] buf = file.read(1..4);
        assert buf.size == 4;
        // TODO array equals does not work in the JIT
        // assert buf == [2, 3, 4, 5];
        assert buf[0] == 2;
        assert buf[1] == 3;
        assert buf[2] == 4;
        assert buf[3] == 5;
    }

    void shouldReadZeroLengthRange() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        Byte[] buf = file.read(0>..<1);
        assert buf.size == 0;
    }

    void shouldThrowForNegativeReadRange() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        try {
            file.read(-1..1);
            assert as "Should have thrown OutOfBounds";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldThrowForTooBigReadRange() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        try {
            file.read(2..11);
            assert as "Should have thrown OutOfBounds";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldThrowTruncatingNonExistentFile() {
        Directory dir  = tmpDir.dirFor("foo").ensure();
        File      file = dir.fileFor("foo.txt");
        assert !file.exists;

        try {
            file.truncate();
            assert as "Should have thrown FileNotFound for missing file";
        } catch (FileNotFound e) {
            assert e.message == file.path.toString();
        } catch (ecstasy.fs.PathException e) {
            // TODO when running in the interpreter we get a PathException with a slightly different
            // message. The JIT is correct.
            assert e.message == "FileNotFound: " + file.path.toString();
        }
    }

    void shouldTruncateFileToZero() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        file.truncate();
        assert file.size == 0;
        assert file.contents.size == 0;
    }

    void shouldTruncateFileToZeroWithNegativeSize() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        file.truncate(-10);
        assert file.size == 0;
        assert file.contents.size == 0;
    }

    void shouldTruncateFileUsingPositiveSize() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        file.truncate(4);
        assert file.size == 4;
        Byte[] contents = file.contents;
        assert contents.size == 4;
        assert contents[0] == 1;
        assert contents[1] == 2;
        assert contents[2] == 3;
        assert contents[3] == 4;
    }

    void shouldTruncateFileUsingNegativeSize() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        file.truncate(-4);
        assert file.size == 6;
        Byte[] contents = file.contents;
        assert contents.size == 6;
        assert contents[0] == 1;
        assert contents[1] == 2;
        assert contents[2] == 3;
        assert contents[3] == 4;
        assert contents[4] == 5;
        assert contents[5] == 6;
    }

    void shouldThrowTruncatingToTooBigSize() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        try {
            file.truncate(11);
            assert as "Should have thrown OutOfBounds";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldThrowTruncatingToNegativeSize() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;

        try {
            file.truncate(-11);
            assert as "Should have thrown OutOfBounds";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void shouldAppendBytesToExistingFile() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        assert file.size == 10;

        Byte[] toAdd = [10, 20, 30];
        file.append(toAdd);
        assert file.size == 13;
        Byte[] contents = file.contents;
        assert contents.size == 13;
        assert contents[0] == 1;
        assert contents[1] == 2;
        assert contents[2] == 3;
        assert contents[3] == 4;
        assert contents[4] == 5;
        assert contents[5] == 6;
        assert contents[6] == 7;
        assert contents[7] == 8;
        assert contents[8] == 9;
        assert contents[9] == 0;
        assert contents[10] == 10;
        assert contents[11] == 20;
        assert contents[12] == 30;
    }

    void shouldAppendBytesToNonExistentFile() {
        Directory dir  = tmpDir.dirFor("foo").ensure();
        File      file = dir.fileFor("foo.txt");
        assert !file.exists;

        Byte[] toAdd = [10, 20, 30];
        file.append(toAdd);
        assert file.exists;
        assert file.size == 3;
        Byte[] contents = file.contents;
        assert contents.size == 3;
        assert contents[0] == 10;
        assert contents[1] == 20;
        assert contents[2] == 30;
    }

    void shouldAppendZeroBytesToExistingFile() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file.contents = bytes;
        assert file.size == 10;

        Byte[] toAdd = [];
        file.append(toAdd);
        assert file.size == 10;
        Byte[] contents = file.contents;
        assert contents[0] == 1;
        assert contents[1] == 2;
        assert contents[2] == 3;
        assert contents[3] == 4;
        assert contents[4] == 5;
        assert contents[5] == 6;
        assert contents[6] == 7;
        assert contents[7] == 8;
        assert contents[8] == 9;
        assert contents[9] == 0;
    }

    void shouldAppendZeroBytesToNonExistentFile() {
// TODO throws unsupported array.freeze(inPlace=False)
//        Directory dir  = tmpDir.dirFor("foo").ensure();
//        File      file = dir.fileFor("foo.txt");
//        assert !file.exists;
//
//        Byte[] toAdd = [];
//        file.append(toAdd);
//        assert file.exists;
//        assert file.size == 0;
//        assert file.contents.size == 0;
    }

    void shouldAppendFileToExistingFile() {
        Directory dir    = tmpDir.dirFor("foo").ensure();

        File      file1  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes1 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file1.contents = bytes1;
        assert file1.size == 10;

        File      file2  = dir.fileFor("bar.txt").ensure();
        Byte[]    bytes2 = [10, 20, 30];
        file2.contents = bytes2;
        assert file2.size == 3;

// TODO nRef.revealAs$p() not implemented
//        file1.append(file2);
//        assert file1.size == 13;
//        Byte[] contents = file1.contents;
//        assert contents.size == 13;
//        assert contents[0] == 1;
//        assert contents[1] == 2;
//        assert contents[2] == 3;
//        assert contents[3] == 4;
//        assert contents[4] == 5;
//        assert contents[5] == 6;
//        assert contents[6] == 7;
//        assert contents[7] == 8;
//        assert contents[8] == 9;
//        assert contents[9] == 0;
//        assert contents[10] == 10;
//        assert contents[11] == 20;
//        assert contents[12] == 30;
    }

    void shouldAppendFileToNonExistentFile() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file1 = dir.fileFor("foo.txt");
        assert !file1.exists;

        File      file2  = dir.fileFor("bar.txt").ensure();
        Byte[]    bytes2 = [10, 20, 30];
        file2.contents = bytes2;
        assert file2.size == 3;

// TODO nRef.revealAs$p() not implemented
//        file1.append(file2);
//        assert file1.exists;
//        assert file1.size == 3;
//        Byte[] contents = file1.contents;
//        assert contents.size == 3;
//        assert contents[0] == 10;
//        assert contents[1] == 20;
//        assert contents[2] == 30;
    }

    void shouldAppendEmptyFileToExistingFile() {
        Directory dir    = tmpDir.dirFor("foo").ensure();
        File      file1  = dir.fileFor("foo.txt").ensure();
        Byte[]    bytes1 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 0];
        file1.contents = bytes1;
        assert file1.size == 10;

        File file2 = dir.fileFor("bar.txt").ensure();
        assert file2.size == 0;

// TODO nRef.revealAs$p() not implemented
//        file1.append(file2);
//        assert file1.size == 10;
//        Byte[] contents = file1.contents;
//        assert contents[0] == 1;
//        assert contents[1] == 2;
//        assert contents[2] == 3;
//        assert contents[3] == 4;
//        assert contents[4] == 5;
//        assert contents[5] == 6;
//        assert contents[6] == 7;
//        assert contents[7] == 8;
//        assert contents[8] == 9;
//        assert contents[9] == 0;
    }

    void shouldAppendEmptyFileToNonExistentFile() {
        Directory dir   = tmpDir.dirFor("foo").ensure();
        File      file1 = dir.fileFor("foo.txt");
        assert !file1.exists;

        File file2 = dir.fileFor("bar.txt").ensure();
        assert file2.size == 0;

// TODO nRef.revealAs$p() not implemented
//        file1.append(file2);
//        assert file1.exists;
//        assert file1.size == 0;
//        assert file1.contents.size == 0;
    }

    void cleanup() {
        tmpDir.dirFor("foo").deleteRecursively();
    }
}
