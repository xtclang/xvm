import ecstasy.fs.FileNode;
import ecstasy.fs.Directory;
import ecstasy.fs.Path;

/**
 * Tests for Directory.
 */
class DirectoryTests {

    static Byte[] NoBytes = [];

    void run() {
        shouldInjectRootDir();
        shouldInjectHomeDir();
        shouldInjectCurDir();
        shouldInjectTmpDir();

        // clean up from any previous tests that may have failed
        @Inject Directory tmpDir;
        tmpDir.dirFor("foo").deleteRecursively();

        shouldCreateChildDir();
        shouldCreateChildFile();
        shouldHaveSize();
        shouldListNames();
        shouldListDirectories();
        shouldListFiles();
        shouldListFilesRecursively();
        shouldDeleteRecursively();
        shouldDeleteRecursivelyNonExistentDir();
    }

    void shouldInjectRootDir() {
        @Inject Directory rootDir;
        assert rootDir.exists;
    }

    void shouldInjectHomeDir() {
        @Inject Directory homeDir;
        assert homeDir.exists;
    }

    void shouldInjectCurDir() {
        @Inject Directory curDir;
        assert curDir.exists;
    }

    void shouldInjectTmpDir() {
        @Inject Directory tmpDir;
        assert tmpDir.exists;
    }

    void shouldCreateChildDir() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        if (dir.exists) {
            dir.delete();
        }
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;
        cleanup(dir);
    }

    void shouldCreateChildFile() {
        @Inject Directory tmpDir;
        File file = tmpDir.fileFor("foo.txt");
        if (file.exists) {
            file.delete();
        }
        assert !file.exists;
        file.ensure();
        assert file.exists;
        cleanup(file);
    }

    void shouldHaveSize() {
        @Inject Directory tmpDir;
        File file = tmpDir.fileFor("foo.txt");
        file.ensure();
        assert file.exists;
        Byte[] bytes = [1, 2, 3, 4];
        file.contents = bytes;
        assert file.size == bytes.size;
        // TODO array equals does not work in the JIT
        // assert file.contents == bytes;
        file.contents = NoBytes;
        assert file.size == 0;
        // TODO array equals does not work in the JIT
        // assert file.contents == NoBytes;
        cleanup(file);
    }

    void shouldListNames() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;

        Directory subDir = dir.dirFor("sub-dir-1").ensure();
        assert subDir.exists;
        String[] names = dir.names().toArray();
        assert names.size == 1;
        assert names[0] == "sub-dir-1";

        File subFile = dir.fileFor("file-1.txt").ensure();
        assert subFile.exists;
        names = dir.names().toArray();
        assert names.size == 2;
        assert (names[0] == "sub-dir-1" && names[1] == "file-1.txt")
            || (names[0] == "file-1.txt" && names[1] == "sub-dir-1");

        cleanup([subDir, subFile, dir]);
    }

    void shouldListDirectories() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;

        Directory subDir1 = dir.dirFor("sub-dir-1").ensure();
        assert subDir1.exists;
        Directory[] dirs = dir.dirs().toArray();
        assert dirs.size == 1;
        assert dirs[0].name == "sub-dir-1";

        File subFile = dir.fileFor("file-1.txt").ensure();
        assert subFile.exists;
        dirs = dir.dirs().toArray();
        assert dirs.size == 1;
        assert dirs[0].name == "sub-dir-1";

        Directory subDir2 = dir.dirFor("sub-dir-2").ensure();
        assert subDir2.exists;
        dirs = dir.dirs().toArray();
        assert dirs.size == 2;
        assert (dirs[0].name == "sub-dir-1" && dirs[1].name == "sub-dir-2")
            || (dirs[0].name == "sub-dir-2" && dirs[1].name == "sub-dir-1");

        cleanup([subDir1, subDir2, subFile, dir]);
    }

    void shouldListFiles() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;

        File subFile1 = dir.fileFor("file-1.txt").ensure();
        assert subFile1.exists;
        File[] files = dir.files().toArray();
        assert files.size == 1;
        assert files[0].name == "file-1.txt";

        Directory subDir = dir.dirFor("sub-dir-1").ensure();
        assert subDir.exists;
        files = dir.files().toArray();
        assert files.size == 1;
        assert files[0].name == "file-1.txt";

        File subFile2 = dir.fileFor("file-2.txt").ensure();
        assert subFile2.exists;
        files = dir.files().toArray();
        assert files.size == 2;
        assert (files[0].name == "file-1.txt" && files[1].name == "file-2.txt")
            || (files[0].name == "file-2.txt" && files[1].name == "file-1.txt");

        cleanup([subFile1, subFile2, subDir, dir]);
    }

    void shouldListFilesRecursively() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;

        File subFile1 = dir.fileFor("file-1.txt").ensure();
        assert subFile1.exists;
        File[] files = dir.filesRecursively().toArray();
        assert files.size == 1;
        assert files[0].name == "file-1.txt";

        Directory subDir = dir.dirFor("sub-dir-1").ensure();
        assert subDir.exists;
        files = dir.filesRecursively().toArray();
        assert files.size == 1;
        assert files[0].name == "file-1.txt";

        File subFile2 = subDir.fileFor("file-2.txt").ensure();
        assert subFile2.exists;
        files = dir.filesRecursively().toArray();
        assert files.size == 2;
        assert (files[0].name == "file-1.txt" && files[1].name == "file-2.txt")
            || (files[0].name == "file-2.txt" && files[1].name == "file-1.txt");

        cleanup([subFile1, subFile2, subDir, dir]);
    }

    void shouldDeleteRecursively() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        dir.ensure();
        assert dir.exists;

        File subFile1 = dir.fileFor("file-1.txt").ensure();
        assert subFile1.exists;
        Directory subDir = dir.dirFor("sub-dir-1").ensure();
        assert subDir.exists;
        File subFile2 = subDir.fileFor("file-2.txt").ensure();
        assert subFile2.exists;

        assert dir.deleteRecursively();
        assert !dir.exists;
    }

    void shouldDeleteRecursivelyNonExistentDir() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("foo");
        assert !dir.exists;
        assert !dir.deleteRecursively();
        assert !dir.exists;
    }

    void cleanup(FileNode node) {
        node.delete();
        assert !node.exists;
    }

    void cleanup(Array<FileNode> files) {
        for (FileNode node : files) {
            if (node.exists) {
                node.delete();
                assert !node.exists;
            }
        }
    }
}
