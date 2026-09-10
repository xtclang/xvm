package org.xvm.asm.constants;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constant;


/**
 * Reports which {@link TypeInfo} cache FIELDS hold constants owned by a foreign pool.
 *
 * <p>A memo hanging off a long-lived TypeInfo that stores whatever the CALLER asked about will keep
 * the caller's constant, and a constant reaches its {@link ConstantPool} through its parent - so one
 * cached constant pins an entire compile. Three separate instances of this were found one run at a
 * time ({@code m_typeAuto}, {@code m_mapMethodsBySignature}, {@code f_cacheById}), each discovered
 * only after fixing the previous one.</p>
 *
 * <p>The existing report columns did not catch any of them: {@code outsideLib} walks
 * {@code getMethods()} and {@code getProperties()} key sets and nothing else, so it read ZERO while
 * all three were live. That is the failure this class exists to end - enumerate the fields rather
 * than discover them one fix at a time.</p>
 */
public final class ForeignCacheAudit {
    private ForeignCacheAudit() {}

    /**
     * @param info       the TypeInfo to audit
     * @param isLibrary  identifies pools that are allowed to be referenced
     *
     * @return field name to count of foreign constants reachable through it, or empty if clean
     */
    public static Map<String, Integer> auditCaches(TypeInfo info, Predicate<ConstantPool> isLibrary) {
        var mapByField = new TreeMap<String, Integer>();
        for (Class<?> clz = info.getClass(); clz != null && clz != Object.class; clz = clz.getSuperclass()) {
            if (!clz.getName().startsWith("org.xvm")) {
                continue;
            }
            for (Field field : clz.getDeclaredFields()) {
                if (field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object oValue;
                try {
                    field.setAccessible(true);
                    oValue = field.get(info);
                } catch (ReflectiveOperationException | RuntimeException | Error e) {
                    continue;
                }
                if (holdsForeign(oValue, isLibrary)) {
                    mapByField.merge(field.getName(), 1, Integer::sum);
                }
            }
        }
        return mapByField;
    }

    /**
     * @return true iff any constant under {@code oRoot} belongs to a pool {@code isLibrary} rejects
     *
     * <p>Short-circuits on the FIRST hit, and visits at most a few hundred nodes. Both matter: the
     * first version counted every foreign constant with a 4000-node budget per field per TypeInfo,
     * and across a library's thousand TypeInfos that turned a 25-second test into minutes. The
     * question is only WHICH FIELD to look at, and one hit answers it as well as a thousand do.</p>
     */
    private static boolean holdsForeign(Object oRoot, Predicate<ConstantPool> isLibrary) {
        if (oRoot == null) {
            return false;
        }
        Set<Object>   setSeen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> queue   = new ArrayDeque<>();
        queue.add(oRoot);
        setSeen.add(oRoot);

        int cVisited = 0;
        while (!queue.isEmpty() && cVisited++ < 300) {
            Object o = queue.poll();
            if (o instanceof Constant constant) {
                ConstantPool pool = constant.getConstantPool();
                if (pool != null && !isLibrary.test(pool)) {
                    return true;
                }
            }
            for (Object oNext : childrenOf(o)) {
                if (oNext != null && setSeen.add(oNext)) {
                    queue.add(oNext);
                }
            }
        }
        return false;
    }

    private static Iterable<Object> childrenOf(Object o) {
        var list = new java.util.ArrayList<Object>();
        switch (o) {
        case Object[] ao   -> java.util.Collections.addAll(list, ao);
        case Collection<?> c -> list.addAll(c);
        case Map<?, ?> map -> map.forEach((k, v) -> { list.add(k); list.add(v); });
        default -> {
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
                        list.add(field.get(o));
                    } catch (ReflectiveOperationException | RuntimeException | Error e) {
                        // inaccessible; nothing to say about it
                    }
                }
            }
        }
        }
        return list;
    }
}
