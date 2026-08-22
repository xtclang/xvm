package org.xvm.runtime.template.reflect;


import java.io.IOException;

import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.regex.Pattern;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.fs.xOSFileNode.NodeHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards handle construction paths that used to publish or mutate partially constructed handles.
 */
public class RefHandleConstructionTest {
    private static final Pattern SELF_REF_PUBLICATION = Pattern.compile(
            "setRef\\s*\\(\\s*this\\s*\\)");

    private static final Pattern REGISTER_CONSTRUCTOR_CALL = Pattern.compile(
            "new\\s+RefHandle\\s*\\(\\s*clzRef\\s*,\\s*frame\\s*,");

    private static final Pattern REFERENT_CONSTRUCTOR = Pattern.compile(
            "public\\s+RefHandle\\s*\\(\\s*TypeComposition\\s+clazz\\s*,\\s*String\\s+sName\\s*,");

    private static final Pattern NODE_CONSTRUCTOR_CALL = Pattern.compile(
            "new\\s+NodeHandle\\s*\\(");

    /**
     * Register refs must be published to frame VarInfo after construction completes. The old
     * constructor-side cache write could expose a partial RefHandle.
     */
    @Test
    public void registerRefPublicationHappensAfterConstruction()
            throws NoSuchMethodException, IOException {
        var constructor = RefHandle.class.getDeclaredConstructor(
                TypeComposition.class, Frame.class, int.class);
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                "register RefHandle construction must go through createRegisterRef()");

        var source = Files.readString(sourcePath("org/xvm/runtime/template/reflect/xRef.java"));
        assertFalse(SELF_REF_PUBLICATION.matcher(source).find(),
                "RefHandle constructors must not publish this into Frame.VarInfo");
        assertFalse(REFERENT_CONSTRUCTOR.matcher(source).find(),
                "referent refs must be initialized through createReferentRef()");
        assertTrue(source.contains("initializeField(REFERENT, hReferent);"),
                "referent factory must initialize the backing field directly after construction");
        assertTrue(source.contains("cacheRegisterRef(infoSrc, new RefHandle(clazz, frame, iVar))"),
                "createRegisterRef() must preserve the frame-local ref cache");
    }

    /**
     * Runtime ops must use the register-ref factory so the post-construction publication rule is
     * enforced by call sites, not just by the direct constructor test.
     */
    @Test
    public void runtimeOpsUseRegisterRefFactory() throws IOException {
        Stream.of("org/xvm/asm/op/MoveRef.java", "org/xvm/asm/op/MoveVar.java")
                .map(RefHandleConstructionTest::sourcePath)
                .map(RefHandleConstructionTest::readString)
                .forEach(source -> {
                    assertFalse(REGISTER_CONSTRUCTOR_CALL.matcher(source).find(),
                            "runtime ops must use RefHandle.createRegisterRef()");
                    assertTrue(source.contains("RefHandle.createRegisterRef("),
                            "runtime ops must preserve frame-local ref caching through the factory");
                });
    }

    /**
     * File node handles must initialize backing store state through a factory. That preserves
     * behavior while avoiding constructor-time public field mutation.
     */
    @Test
    public void fileNodeHandlesUseFactoryForStoreField()
            throws NoSuchMethodException {
        var constructor = NodeHandle.class.getDeclaredConstructor(TypeComposition.class, Path.class);
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                "NodeHandle construction must go through NodeHandle.create()");

        Stream.of("org/xvm/runtime/template/_native/fs/xOSDirectory.java",
                  "org/xvm/runtime/template/_native/fs/xOSFile.java")
                .map(RefHandleConstructionTest::sourcePath)
                .map(RefHandleConstructionTest::readString)
                .forEach(source -> {
                    assertFalse(NODE_CONSTRUCTOR_CALL.matcher(source).find(),
                            "file-node templates must use NodeHandle.create()");
                    assertTrue(source.contains("NodeHandle.create("),
                            "file-node templates must preserve store-field initialization");
                });
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Path sourcePath(String source) {
        var path = Path.of("src/main/java", source);
        return Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source);
    }
}
