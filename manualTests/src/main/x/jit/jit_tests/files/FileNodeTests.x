import ecstasy.fs.FileAlreadyExists;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileStore;
import ecstasy.fs.Path;

/**
 * Tests for FileNode.
 */
class FileNodeTests {

    Path RootPath = new Path("/");

    void run() {
        shouldHaveStorage();
        shouldHaveNullParentIfRoot();
        shouldHaveParentIfCurrentDir();
        shouldHaveParent();
        shouldHavePath();
        shouldHaveName();
        shouldHaveEmptyNameIfRoot();
        shouldExist();
        shouldHaveZeroSizeWhenNotExisting();
        shouldHaveCreatedTime();
        shouldHaveCreatedTimeWhenNotExisting();
        shouldHaveModifiedTime();
        shouldHaveModifiedTimeWhenNotExisting();
        shouldHaveAccessedTime();
        shouldHaveAccessedTimeWhenNotExisting();
        shouldBeReadable();
        shouldBeWritable();
        shouldCreateAndDelete();
        shouldEnsureAndDelete();
        shouldRename();
        shouldReplaceExistingFile();
        shouldEmitListing();
    }

    void shouldHaveStorage() {
        @Inject FileStore storage;
        assert FileNode node := storage.find(RootPath);
        assert node.store == storage;
    }

    void shouldHaveNullParentIfRoot() {
        @Inject FileStore storage;
        assert FileNode node := storage.find(RootPath);
        assert node.parent == Null;
    }

    void shouldHaveParentIfCurrentDir() {
        @Inject FileStore storage;
        assert FileNode node := storage.find(new Path("."));
        assert node.path.absolute;
        assert Directory parent ?= node.parent;
        assert parent.path == node.path.parent;
    }

    void shouldHaveParent() {
        @Inject FileStore storage;
        Path       path   = new Path("/one/two");
        FileNode   node   = storage.dirFor(path);
        Directory? parent = node.parent;
        assert parent.is(Directory);
        assert parent.path == path.parent;
    }

    void shouldHavePath() {
        @Inject FileStore storage;
        Path     path = new Path("/one/two");
        FileNode node = storage.dirFor(path);
        assert node.path == path;
    }

    void shouldHaveName() {
        @Inject FileStore storage;
        Path     path = new Path("/one/two");
        FileNode node = storage.dirFor(path);
        assert node.name == "two";
    }

    void shouldHaveEmptyNameIfRoot() {
        @Inject FileStore storage;
        FileNode node = storage.dirFor(RootPath);
        assert node.name == "";
    }

    void shouldExist() {
        @Inject FileStore storage;
        FileNode rootNode = storage.dirFor(RootPath);
        assert rootNode.exists;

        Path     path = new Path("one/two");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
    }

    void shouldHaveZeroSizeWhenNotExisting() {
        @Inject FileStore storage;
        Path     path = new Path("one/two");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        assert node.size == 0;
    }

    void shouldHaveCreatedTime() {
        @Inject FileStore storage;
        FileNode rootNode = storage.dirFor(RootPath);
        assert rootNode.created > Time.EPOCH;
    }

    void shouldHaveCreatedTimeWhenNotExisting() {
        @Inject FileStore storage;
        Path     path = new Path("one/two");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        assert node.created >= Time.EPOCH;
    }

    void shouldHaveModifiedTime() {
        @Inject FileStore storage;
        FileNode rootNode = storage.dirFor(RootPath);
        assert rootNode.modified > Time.EPOCH;
    }

    void shouldHaveModifiedTimeWhenNotExisting() {
        @Inject FileStore storage;
        Path     path = new Path("one/two");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        assert node.modified >= Time.EPOCH;
    }

    void shouldHaveAccessedTime() {
        @Inject FileStore storage;
        FileNode rootNode = storage.dirFor(RootPath);
        assert rootNode.accessed > Time.EPOCH;
    }

    void shouldHaveAccessedTimeWhenNotExisting() {
        @Inject FileStore storage;
        Path     path = new Path("one/two");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        assert node.accessed >= Time.EPOCH;
    }

    void shouldBeReadable() {
        @Inject FileStore storage;
        Path     path = new Path(".");
        FileNode node = storage.dirFor(path);
        assert node.readable;
    }

    void shouldBeWritable() {
        @Inject FileStore storage;
        Path     path = new Path(".");
        FileNode node = storage.dirFor(path);
        assert node.writable;
    }

    void shouldCreateAndDelete() {
        @Inject FileStore storage;
        Path     path = new Path("./foo");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        assert node.create();
        assert node.exists;
        assert !node.create();
        assert node.delete();
        assert !node.exists;
    }

    void shouldEnsureAndDelete() {
        @Inject FileStore storage;
        Path     path = new Path("./foo");
        FileNode node = storage.dirFor(path);
        assert !node.exists;
        node = node.ensure();
        assert node.exists;
        node = node.ensure();
        assert node.delete();
        assert !node.exists;
    }

    void shouldRename() {
        @Inject FileStore storage;
        Path     path1 = new Path("./foo");
        FileNode node1 = storage.dirFor(path1);
        Path     path2 = new Path("./bar");
        FileNode node2 = storage.dirFor(path2);
        assert !node1.exists;
        assert !node2.exists;
        assert node1.create();
        assert node1.exists;
        assert FileNode node3 := node1.renameTo(path2.name);
        assert node3.exists;
        assert !node1.exists;
        assert node3.delete();
        assert !node3.exists;
    }

    void shouldReplaceExistingFile() {
        @Inject Directory tmpDir;
        Directory dir = tmpDir.dirFor("file-node-rename-overwrite");
        assert dir.create();
        try {
            File source = dir.fileFor("replacement").ensure();
            File target = dir.fileFor("target").ensure();
            source.contents = [1];
            target.contents = [2];

            assert FileNode same := source.renameTo(source.name);
            assert same.path == source.path;
            assert !source.renameTo(target.name);
            assert !source.renameTo("");

            try {
                source.store.move(source.path, target.path);
                assert;
            } catch (FileAlreadyExists e) {}

            Directory subdir = dir.dirFor("subdir").ensure();
            Directory other = dir.dirFor("other").ensure();
            assert !source.renameTo("subdir/target", allowOverwrite=True);
            assert !source.renameTo(subdir.name, allowOverwrite=True);
            assert !subdir.renameTo(target.name, allowOverwrite=True);
            assert !subdir.renameTo(other.name, allowOverwrite=True);
            assert subdir.exists && other.exists;
            if (Directory alias := dir.findDir("SUBDIR")) {
                assert !subdir.renameTo("SUBDIR");
                subdir.store.move(subdir.path, alias.path);
                assert subdir.exists;
            }
            assert Directory moved := subdir.renameTo("moved", allowOverwrite=True);
            assert moved.exists && !subdir.exists;
            assert source.contents[0] == 1;
            assert target.contents[0] == 2;

            assert FileNode renamed := source.renameTo(target.name, allowOverwrite=True);
            assert renamed.path == target.path;
            assert renamed.as(File).contents[0] == 1;
            assert !source.exists;
        } finally {
            dir.deleteRecursively();
        }
    }

    void shouldEmitListing() {
        @Inject FileStore storage;
        Path     path1 = new Path("./foo");
        Path     path2 = new Path("./foo/bar1");
        Path     path3 = new Path("./foo/bar2");
        FileNode node1 = storage.dirFor(path1);
        FileNode node2 = storage.dirFor(path2);
        FileNode node3 = storage.dirFor(path3);

        assert !node1.exists;

        Appender<Char> buf = node1.emitListing(new StringBuffer());
        assert buf.toString() == "";

        node1.ensure();
        assert node1.exists;
        buf = node1.emitListing(new StringBuffer());
        assert buf.toString() == "";

        node2.ensure();
        assert node2.exists;
        buf = node1.emitListing(new StringBuffer());
        assert buf.toString() == $|  +- bar1/
                                  |
                                  ;

        node3.ensure();
        assert node3.exists;
        buf = node1.emitListing(new StringBuffer());

        String list1 = $|  +- bar1/
                        |  +- bar2/
                        |
                        ;

        String list2 = $|  +- bar2/
                        |  +- bar1/
                        |
                        ;

        String actual = buf.toString();
        assert actual == list1 || actual == list2;

        node2.delete();
        assert !node2.exists;
        node3.delete();
        assert !node3.exists;
        node1.delete();
        assert !node1.exists;
    }
}
