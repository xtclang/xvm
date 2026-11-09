import ecstasy.fs.FileStore;
import ecstasy.fs.FileNode;
import ecstasy.fs.Path;

/**
 * Tests for FileNode.
 */
class FileNodeTests {

    Path RootPath = new Path("/");

    void run() {
        shouldHaveStorage();
        shouldHaveNullParentIfRoot();
        shouldHaveNullParentIfCurrentDir();
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
        shouldEmitListing();
    }

    void shouldHaveStorage() {
        @Inject FileStore storage;
        assert FileNode node := storage.find(RootPath);
//        assert node.store == storage;
    }

    void shouldHaveNullParentIfRoot() {
        @Inject FileStore storage;
        assert FileNode node := storage.find(RootPath);
        assert node.parent == Null;
    }

    // TODO node.parent throws due to assertion in FileNode.x parent.get() as the
    // current directory path has a Null parent path
    // The same would happen for a FileNode created from the parent path ".."
    void shouldHaveNullParentIfCurrentDir() {
//        @Inject FileStore storage;
//        assert FileNode node := storage.find(new Path("."));
//        console.print(node.parent);
//        assert node.parent == Null;
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
// TODO requires @Lazy support
//        @Inject FileStore storage;
//        FileNode rootNode = storage.dirFor(RootPath);
//        assert rootNode.created > Time.EPOCH;
    }

    void shouldHaveCreatedTimeWhenNotExisting() {
// TODO requires @Lazy support
//        @Inject FileStore storage;
//        Path     path = new Path("one/two");
//        FileNode node = storage.dirFor(path);
//        assert !node.exists;
//        assert node.created >= Time.EPOCH;
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
