import ecstasy.fs.FileStore;
import ecstasy.fs.FileNode;
import ecstasy.fs.Path;

/**
 * Tests for FileStore.
 */
class FileStoreTests {

    void run() {
        shouldHaveCapacity();
        shouldHaveRoot();
        shouldFindCurrentDirectory();
        shouldGetAbsoluteDirForPath();
        shouldGetRelativeDirForPath();
        shouldGetFileForPath();
    }

    void shouldHaveCapacity() {
        @Inject FileStore storage;
        assert storage.capacity > 0;
        assert storage.bytesFree <= storage.capacity;
        assert storage.bytesUsed == (storage.capacity - storage.bytesFree);
    }

    void shouldHaveRoot() {
        @Inject FileStore storage;
        assert FileNode dir := storage.find(storage.root.path);
        assert dir.is(Directory);
        assert dir.toString() == storage.root.toString();
        assert dir.store == storage;
        Path path = storage.root.path;
        assert path.parent == Null;
        assert path.form == Root;
        assert path.name == "/";
        assert path.size == 1;
        assert path.absolute == True;
    }

    void shouldFindCurrentDirectory() {
        @Inject FileStore storage;
        assert FileNode dir := storage.find(new Path("."));
        assert dir.is(Directory);
    }

    void shouldFindParentDirectory() {
        @Inject FileStore storage;
        assert FileNode dir := storage.find(new Path(".."));
        assert dir.is(Directory);
    }

    void shouldGetAbsoluteDirForPath() {
        @Inject FileStore storage;
        Path      path = new Path("/one/two");
        Directory dir  = storage.dirFor(path);
        assert dir.toString() == "/one/two";
        assert dir.path == path;
    }

    void shouldGetRelativeDirForPath() {
        @Inject FileStore storage;
        Path      path = new Path("one/two");
        Directory dir  = storage.dirFor(path);
        assert dir.toString() == "one/two";
        assert dir.path == path;
    }

    void shouldGetFileForPath() {
        @Inject FileStore storage;
        Path path = new Path("/one/two/three.txt");
        File file = storage.fileFor(path);
        assert file.toString() == "/one/two/three.txt";
        assert file.path == path;
    }
}
