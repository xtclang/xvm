package org.xvm.asm;


import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


/**
 * Finds the reference path from a root object to an object that should have been collected.
 *
 * <p>This exists because every cheaper method failed to name a retainer. A weak-reference counter
 * says HOW MANY objects survive and a creation-site tag says WHICH, but neither can say WHO holds
 * them - that needs a reverse reference, and the JVM does not offer one. JFR's OldObjectSample can,
 * in principle, but it SAMPLES: a run that retained tens of pools produced 248 samples of which 232
 * were rooted in live thread stacks, naming nothing.</p>
 *
 * <p>So: walk forward from a root, breadth-first, and report the first path that reaches the
 * target. Breadth-first matters - the shortest path is the one a reader can act on, and a
 * depth-first walk through a type graph produces a hundred-hop path through incidental references.
 * Diagnostic only; nothing calls this on a normal path.</p>
 */
public final class RetainerPath {
    private RetainerPath() {}

    /**
     * @param oRoot    where to start walking
     * @param test     identifies the object being hunted
     * @param cMaxHops how deep to search before giving up
     *
     * @return a human-readable path from the root to a matching object, or null if none is reachable
     */
    public static String find(Object oRoot, Predicate<Object> test, int cMaxHops) {
        Set<Object>         setSeen  = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<List<Object>> queue    = new ArrayDeque<>();
        Deque<List<String>> queueVia = new ArrayDeque<>();

        queue.add(List.of(oRoot));
        queueVia.add(List.of(describe(oRoot)));
        setSeen.add(oRoot);

        while (!queue.isEmpty()) {
            List<Object> path = queue.poll();
            List<String> via  = queueVia.poll();
            if (path.size() > cMaxHops) {
                continue;
            }
            Object oCur = path.get(path.size() - 1);

            for (Edge edge : edgesOf(oCur)) {
                Object oNext = edge.target();
                if (oNext == null || !setSeen.add(oNext)) {
                    continue;
                }
                var viaNext = new ArrayList<>(via);
                viaNext.add(edge.label() + " -> " + describe(oNext));
                if (test.test(oNext)) {
                    return String.join("\n        ", viaNext);
                }
                var pathNext = new ArrayList<>(path);
                pathNext.add(oNext);
                queue.add(pathNext);
                queueVia.add(viaNext);
            }
        }
        return null;
    }

    private record Edge(String label, Object target) {}

    private static List<Edge> edgesOf(Object o) {
        var list = new ArrayList<Edge>();
        if (o instanceof Object[] ao) {
            for (int i = 0; i < ao.length; ++i) {
                list.add(new Edge("[" + i + "]", ao[i]));
            }
            return list;
        }
        if (o instanceof Collection<?> coll) {
            int i = 0;
            for (Object oElem : coll) {
                list.add(new Edge("elem[" + i++ + "]", oElem));
            }
            return list;
        }
        if (o instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                list.add(new Edge("key", entry.getKey()));
                list.add(new Edge("val[" + describe(entry.getKey()) + "]", entry.getValue()));
            }
            return list;
        }
        // Reflect only over our own classes. Walking JDK internals adds hops that name nothing and
        // trips module access checks; the interesting edges are all in org.xvm.
        for (Class<?> clz = o.getClass(); clz != null && clz != Object.class; clz = clz.getSuperclass()) {
            if (!clz.getName().startsWith("org.xvm")) {
                continue;
            }
            for (Field field : clz.getDeclaredFields()) {
                if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    list.add(new Edge(clz.getSimpleName() + "." + field.getName(), field.get(o)));
                } catch (ReflectiveOperationException | RuntimeException | Error e) {
                    // inaccessible; nothing to say about it
                }
            }
        }
        return list;
    }

    private static String describe(Object o) {
        if (o == null) {
            return "null";
        }
        Class<?> clz = o.getClass();
        String   s   = clz.isArray()
                ? clz.getComponentType().getSimpleName() + "[" + Array.getLength(o) + "]"
                : clz.getSimpleName();
        if (o instanceof ConstantPool pool) {
            s += "(" + pool.describeOwner() + ")";
        }
        return s;
    }
}
