package org.xtclang.ecstasy.fs;

import org.xtclang.ecstasy.nConst;
import org.xtclang.ecstasy.nEnum;
import org.xtclang.ecstasy.Nullable;
import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code ecstasy.fs.Path}.
 */
public class Path extends nConst {

    /**
     * Dummy constructor, the JIT will generate the real constructors.
     */
    protected Path() {
        super(null);
    }

    public Object parent;

    public String name;

    public long size;

    public boolean absolute;

    public ElementForm form;

    private java.nio.file.Path $javaPath;

    /**
     * The property {@code static Path ROOT = new Path(Null, Root);}.
     */
    public static Path ROOT;

    /**
     * The property {@code static Path CURRENT = new Path(Null, Current);}.
     */
    public static Path CURRENT;

    /**
     * The property {@code static Path PARENT = new Path(Null, Parent);}.
     */
    public static Path PARENT;

    /**
     * The native property getter {@code Path? parent;}.
     */
    public Object parent$get(Ctx ctx) {
        Object parent = this.parent;
        if (parent == null) {
            // will only be null if created from the $fromJavaPath method
            java.nio.file.Path path = $javaPath.getParent();
            if (path == null) {
                parent = this.parent = Nullable.Null.$INSTANCE;
            } else {
                parent = this.parent = $fromJavaPath(ctx, path);
            }
        }
        return parent;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    public static Path $fromJavaPath(Ctx ctx, java.nio.file.Path javaPath) {
        int              size = javaPath.getNameCount();
        java.lang.String name = size == 0 ? "/" : javaPath.getFileName().toString();

        if (name.isEmpty()) {
            return CURRENT;
        }

        Path path = new Path();
        boolean absolute = javaPath.isAbsolute();

        path.parent    = null;
        path.$javaPath = javaPath;
        path.name      = String.of(ctx, name);
        path.size      = absolute ? size + 1 : size;
        path.absolute  = absolute;
        path.form      = switch (name) {
            case "/"  -> ElementForm.Root.$INSTANCE;
            case ".." -> ElementForm.Parent.$INSTANCE;
            case "."  -> ElementForm.Current.$INSTANCE;
            default   -> ElementForm.Name.$INSTANCE;
        };
        return path;
    }

    java.nio.file.Path $getJavaPath() {
        java.nio.file.Path javaPath = $javaPath;
        if (javaPath == null) {
            if (parent instanceof Path parentPath) {
                javaPath = $javaPath = parentPath.$getJavaPath().resolve(name.toString());
            } else {
                javaPath = $javaPath = java.nio.file.Paths.get(name.toString());
            }
        }
        return javaPath;
    }

    // ----- inner enum ElementForm ----------------------------------------------------------------

    /**
     * The native implementation of {@code ecstasy.fs.Path.ElementForm}.
     */
    public static class ElementForm extends nEnum {
        private ElementForm(long ordinal, String name, String text, long depth) {
            super(null);
            this.$ordinal = ordinal;
            this.$name    = name;
            this.text     = text;
            this.depth    = depth;
        }

        public final long $ordinal;
        public final String $name;
        public final String text;
        public final long depth;

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return $ordinal;
        }

        @Override
        public java.lang.String toString() {
            return "ElementForm." + $name.toString();
        }

        public static class Root extends ElementForm {
            public static Root $INSTANCE = new Root();
            public Root() {
                super(0, String.of(null, "Root"), String.of(null, "/"), 0L);
            }
        }

        public static class Parent extends ElementForm {
            public static Parent $INSTANCE = new Parent();
            public Parent() {
                super(1, String.of(null, "Parent"), String.of(null, ".."), -1L);
            }
        }

        public static class Current extends ElementForm {
            public static Current $INSTANCE = new Current();
            public Current() {
                super(2, String.of(null, "Current"), String.of(null, "."), 0L);
            }
        }

        public static class Name extends ElementForm {
            public static Name $INSTANCE = new Name();
            public Name() {
                super(3, String.of(null, "Name"), String.of(null, "name"), 1L);
            }
        }
    }
}
