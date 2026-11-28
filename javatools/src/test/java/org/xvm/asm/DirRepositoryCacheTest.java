package org.xvm.asm;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;

import java.time.Instant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.DirRepository.CacheDirectory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests of where and how {@link DirRepository} keeps its persistent cache of module names: only in
 * a directory of the user's own that no one else can write, in a file named for the repository's
 * canonical path alone, written by an atomic move that never follows a link, pruned of files that
 * have not been written for a long time, and never trusted over the module that a file actually
 * holds.
 */
public class DirRepositoryCacheTest {
    @TempDir
    Path temp;

    /**
     * A directory of this user's is made for its owner alone and holds the repository's cache.
     */
    @Test
    public void cachesInAPrivateDirectory() throws IOException {
        Path dirCache = temp.resolve("cache");
        Path dirRepo  = repository("repo", "one");

        assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(dirCache)), "one");

        assertEquals(1, cacheFiles(dirCache).size());
        if (posix()) {
            assertEquals("rwx------",
                    PosixFilePermissions.toString(Files.getPosixFilePermissions(dirCache)));
        }
    }

    /**
     * A candidate that is not to be used gives way to the next one.
     */
    @Test
    public void fallsBackToTheNextCandidate() throws IOException {
        Path fileRefused = Files.createFile(temp.resolve("refused"));
        Path dirCache    = temp.resolve("cache");
        Path dirRepo     = repository("repo", "one");

        assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(fileRefused, dirCache)), "one");

        assertEquals(1, cacheFiles(dirCache).size());
        assertTrue(Files.isRegularFile(fileRefused));
    }

    /**
     * A candidate that cannot be made, in a parent that this user cannot write, leaves the
     * repository without a cache, and it still loads its modules.
     */
    @Test
    public void uncreatableCacheDirectoryStillLoads() throws IOException {
        assumeTrue(posix(), "the file system has no POSIX permissions");
        Path dirParent = Files.createDirectory(temp.resolve("parent"));
        Path dirRepo   = repository("repo", "one");
        Files.setPosixFilePermissions(dirParent, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeTrue(!Files.isWritable(dirParent), "this user can write any directory");

            assertLoads(new DirRepository(dirRepo.toFile(), true,
                    cacheIn(dirParent.resolve("cache"))), "one");

            assertFalse(Files.exists(dirParent.resolve("cache"), LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.setPosixFilePermissions(dirParent, PosixFilePermissions.fromString("rwx------"));
        }
    }

    /**
     * A link at the cache directory's name is not used, though it leads to a directory of this
     * user's that no one else can write.
     */
    @Test
    public void linkedCacheDirectoryIsNotUsed() throws IOException {
        Path dirReal = Files.createDirectory(temp.resolve("real"));
        Path dirLink = link(temp.resolve("cache"), dirReal);
        Path dirRepo = repository("repo", "one");

        assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(dirLink)), "one");

        assertEquals(List.of(), cacheFiles(dirReal));
    }

    /**
     * A cache directory that another user owns is not used.
     */
    @Test
    public void cacheDirectoryOwnedByAnotherUserIsNotUsed() throws IOException {
        Path dirCache = Files.createDirectory(temp.resolve("cache"));
        Path dirRepo  = repository("repo", "one");

        assertLoads(new DirRepository(dirRepo.toFile(), true,
                new CacheDirectory(List.of(dirCache), null, path -> false)), "one");

        assertEquals(List.of(), cacheFiles(dirCache));
    }

    /**
     * A cache directory that its group or others can write is not used.
     */
    @Test
    public void cacheDirectoryOthersCanWriteIsNotUsed() throws IOException {
        assumeTrue(posix(), "the file system has no POSIX permissions");
        Path dirRepo = repository("repo", "one");
        for (String sPerms : List.of("rwxrwx---", "rwx---rwx")) {
            Path dirCache = Files.createDirectory(temp.resolve("cache-" + sPerms));
            Files.setPosixFilePermissions(dirCache, PosixFilePermissions.fromString(sPerms));

            assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(dirCache)), "one");

            assertEquals(List.of(), cacheFiles(dirCache), sPerms);
        }
    }

    /**
     * A cache directory in a parent that anyone can write is used only if the parent has the
     * sticky bit, which keeps others from renaming the directory away.
     */
    @Test
    public void parentAnyoneCanWriteNeedsTheStickyBit() throws IOException {
        assumeTrue(temp.getFileSystem().supportedFileAttributeViews().contains("unix"),
                "the file system has no Unix modes");
        Path dirRepo = repository("repo", "one");

        Path dirOpen = Files.createDirectory(temp.resolve("open"));
        Files.setAttribute(dirOpen, "unix:mode", 0777);
        assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(dirOpen.resolve("cache"))),
                "one");
        assertEquals(List.of(), cacheFiles(dirOpen.resolve("cache")));

        Path dirSticky = Files.createDirectory(temp.resolve("sticky"));
        Files.setAttribute(dirSticky, "unix:mode", 01777);
        assertLoads(new DirRepository(dirRepo.toFile(), true, cacheIn(dirSticky.resolve("cache"))),
                "one");
        assertEquals(1, cacheFiles(dirSticky.resolve("cache")).size());
    }

    /**
     * Two repositories whose canonical paths have the same {@link String#hashCode} keep their
     * caches in two files, and each repository reads its own.
     */
    @Test
    public void collidingPathsGetSeparateCaches() throws IOException {
        Path dirCache = temp.resolve("cache");
        Path dirOne   = repository("Aa", "one");
        Path dirTwo   = repository("BB", "two");
        assertEquals(dirOne.toFile().getCanonicalPath().hashCode(),
                dirTwo.toFile().getCanonicalPath().hashCode());

        CacheDirectory cache = cacheIn(dirCache);
        assertLoads(new DirRepository(dirOne.toFile(), true, cache), "one");
        assertLoads(new DirRepository(dirTwo.toFile(), true, cache), "two");

        assertEquals(2, cacheFiles(dirCache).size());
        assertEquals(Set.of(name("one")), new DirRepository(dirOne.toFile(), true, cache).getModuleNames());
        assertEquals(Set.of(name("two")), new DirRepository(dirTwo.toFile(), true, cache).getModuleNames());
    }

    /**
     * A cache that names the wrong module for a file is caught when the module is loaded, the
     * module that was asked for is still found, and the cache is saved again. The two module files
     * trade places with their size and time kept, so the cache that the first repository saved
     * names each file's old module.
     */
    @Test
    public void cacheNamingTheWrongModuleIsDetected() throws IOException {
        Path dirCache = temp.resolve("cache");
        Path dirRepo  = repository("repo", "one", "two");
        File fileOne  = dirRepo.resolve("one.xtc").toFile();
        File fileTwo  = dirRepo.resolve("two.xtc").toFile();
        assertEquals(fileOne.length(), fileTwo.length());

        long timestamp = 1_700_000_000_000L;
        assertTrue(fileOne.setLastModified(timestamp));
        assertTrue(fileTwo.setLastModified(timestamp));

        CacheDirectory cache = cacheIn(dirCache);
        assertEquals(Set.of(name("one"), name("two")),
                new DirRepository(dirRepo.toFile(), true, cache).getModuleNames());
        Path   pathCache = cacheFiles(dirCache).getFirst();
        byte[] abStale   = Files.readAllBytes(pathCache);

        Path pathSwap = dirRepo.resolve("swap");
        Files.move(fileOne.toPath(), pathSwap);
        Files.move(fileTwo.toPath(), fileOne.toPath());
        Files.move(pathSwap, fileTwo.toPath());
        assertTrue(fileOne.setLastModified(timestamp));
        assertTrue(fileTwo.setLastModified(timestamp));

        DirRepository repo = new DirRepository(dirRepo.toFile(), true, cache);
        assertLoads(repo, "one");
        assertLoads(repo, "two");
        assertFalse(Arrays.equals(abStale, Files.readAllBytes(pathCache)));
        assertLoads(new DirRepository(dirRepo.toFile(), true, cache), "one");
    }

    /**
     * A link at the cache file's name is neither read nor written through: the repository scans
     * its files, replaces the link with a cache file of its own by an atomic move, leaves what the
     * link named as it was, and leaves no temporary file behind.
     */
    @Test
    public void cacheFileLinkIsNeitherReadNorWrittenThrough() throws IOException {
        Path dirCache = temp.resolve("cache");
        Path dirRepo  = repository("repo", "one");

        CacheDirectory cache = cacheIn(dirCache);
        assertLoads(new DirRepository(dirRepo.toFile(), true, cache), "one");
        Path pathCache = cacheFiles(dirCache).getFirst();

        // a valid cache for this repository, but somewhere else, and a link to it
        Path   pathElsewhere = Files.copy(pathCache, temp.resolve("elsewhere.cache"));
        byte[] abElsewhere   = Files.readAllBytes(pathElsewhere);
        Files.delete(pathCache);
        link(pathCache, pathElsewhere);

        assertLoads(new DirRepository(dirRepo.toFile(), true, cache), "one");

        assertFalse(Files.isSymbolicLink(pathCache));
        assertTrue(Files.isRegularFile(pathCache, LinkOption.NOFOLLOW_LINKS));
        assertArrayEquals(abElsewhere, Files.readAllBytes(pathElsewhere));
        try (Stream<Path> files = Files.list(dirCache)) {
            assertEquals(List.of(pathCache), files.toList());
        }
    }

    /**
     * The first use of a cache directory deletes the cache and temporary files that have not been
     * written for longer than the maximum age, but only this user's regular files, and nothing
     * that a link names.
     */
    @Test
    public void oldCacheFilesArePruned() throws IOException {
        Path     dirCache = Files.createDirectory(temp.resolve("cache"));
        Path     dirRepo  = repository("repo", "one");
        FileTime timeOld  = FileTime.from(Instant.now().minus(CacheDirectory.MAX_AGE.multipliedBy(2)));

        Path pathOld     = aged(Files.createFile(dirCache.resolve("old.cache")), timeOld);
        Path pathTemp    = aged(Files.createFile(dirCache.resolve("old.cache123.tmp")), timeOld);
        Path pathFresh   = Files.createFile(dirCache.resolve("fresh.cache"));
        Path pathOther   = aged(Files.createFile(dirCache.resolve("old.txt")), timeOld);
        Path pathForeign = aged(Files.createFile(dirCache.resolve("foreign.cache")), timeOld);
        Path pathTarget  = aged(Files.createFile(temp.resolve("target.cache")), timeOld);
        Path pathLink    = link(dirCache.resolve("link.cache"), pathTarget);

        CacheDirectory cache = new CacheDirectory(List.of(dirCache), null,
                path -> !path.equals(pathForeign));
        assertLoads(new DirRepository(dirRepo.toFile(), true, cache), "one");

        assertFalse(Files.exists(pathOld, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(pathTemp, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(pathFresh));
        assertTrue(Files.exists(pathOther));
        assertTrue(Files.exists(pathForeign));
        assertTrue(Files.isSymbolicLink(pathLink));
        assertTrue(Files.exists(pathTarget));
    }

    /**
     * The user's cache directory is the platform's, and repositories in the temporary directory
     * are throwaway ones that are not cached at all.
     */
    @Test
    public void userCacheDirectoryIsThePlatformsAndSkipsTheTemporaryDirectory() throws IOException {
        Path dirHome     = Files.createDirectory(temp.resolve("home"));
        Path dirLocal    = Files.createDirectory(temp.resolve("local"));
        Path dirXdg      = Files.createDirectory(temp.resolve("xdg"));
        Path dirTemp     = Files.createDirectory(temp.resolve("tmp"));
        Path dirFallback = dirTemp.resolve("xtclang-dir-repository-a_b");
        Map<String, String> properties = Map.of(
                "user.home",      dirHome.toString(),
                "user.name",      "a/b",
                "java.io.tmpdir", dirTemp.toString());

        assertEquals(List.of(dirXdg.resolve("xtclang/dir-repository"), dirFallback),
                CacheDirectory.forUser("Linux", Map.of("XDG_CACHE_HOME", dirXdg.toString())::get,
                        properties::get).getCandidates());
        assertEquals(List.of(dirHome.resolve(".cache/xtclang/dir-repository"), dirFallback),
                CacheDirectory.forUser("Linux", Map.of("XDG_CACHE_HOME", "relative")::get,
                        properties::get).getCandidates());
        assertEquals(List.of(dirHome.resolve("Library/Caches/xtclang/dir-repository"), dirFallback),
                CacheDirectory.forUser("Mac OS X", Map.of("XDG_CACHE_HOME", dirXdg.toString())::get,
                        properties::get).getCandidates());
        assertEquals(List.of(dirLocal.resolve("xtclang/dir-repository"), dirFallback),
                CacheDirectory.forUser("Windows 11", Map.of("LOCALAPPDATA", dirLocal.toString())::get,
                        properties::get).getCandidates());
        assertEquals(List.of(dirFallback),
                CacheDirectory.forUser("Windows 11", Map.<String, String>of()::get,
                        properties::get).getCandidates());

        CacheDirectory cache = CacheDirectory.forUser("Linux", Map.<String, String>of()::get,
                properties::get);
        Path dirCache = dirHome.resolve(".cache/xtclang/dir-repository");

        assertLoads(new DirRepository(repository("tmp/repo", "one").toFile(), true, cache), "one");
        assertEquals(List.of(), cacheFiles(dirCache));

        assertLoads(new DirRepository(repository("repo", "one").toFile(), true, cache), "one");
        assertEquals(1, cacheFiles(dirCache).size());
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @param sDir      the repository directory's path in the test's directory
     * @param asModule  the simple names of the modules to write there, each in a file of its name
     *
     * @return the repository directory
     */
    private Path repository(String sDir, String... asModule) throws IOException {
        Path dir = Files.createDirectory(temp.resolve(sDir));
        for (String sModule : asModule) {
            new FileStructure(name(sModule)).writeTo(dir.resolve(sModule + ".xtc").toFile());
        }
        return dir;
    }

    /**
     * @param candidates  the candidate cache directories, in order of preference
     *
     * @return a cache directory that the current user must own, for repositories anywhere
     */
    private static CacheDirectory cacheIn(Path... candidates) {
        return new CacheDirectory(List.of(candidates), null);
    }

    /**
     * @param link    the link to make
     * @param target  what the link names
     *
     * @return the link, unless the file system or the user cannot make links, which skips the test
     */
    private static Path link(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "links cannot be made here: " + e);
            return link;
        }
    }

    private static Path aged(Path path, FileTime time) throws IOException {
        Files.setLastModifiedTime(path, time);
        return path;
    }

    private static String name(String sModule) {
        return sModule + ".test.xtclang.org";
    }

    private static void assertLoads(DirRepository repo, String sModule) {
        ModuleStructure module = repo.loadModule(name(sModule));
        assertNotNull(module, sModule);
        assertEquals(name(sModule), module.getIdentityConstant().getName());
    }

    private static List<Path> cacheFiles(Path dirCache) throws IOException {
        if (!Files.isDirectory(dirCache)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dirCache)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".cache")).toList();
        }
    }

    private boolean posix() {
        return temp.getFileSystem().supportedFileAttributeViews().contains("posix");
    }
}
