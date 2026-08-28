package org.xvm.runtime;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Pins the per-view state classification for the handle classes that opt into mutable views.
 *
 * <p>{@link ObjectHandle#cloneAs} does not copy an object - it creates another ACCESS VIEW of the
 * same object under a different {@code TypeComposition}, using {@code super.clone()} to duplicate
 * the view while carrying the shared state along. That shallow copy is only safe if every field it
 * duplicates is one of:</p>
 *
 * <ol>
 *   <li><b>view identity</b> - {@code m_clazz}, which {@code cloneAs} immediately overwrites, and
 *       {@code GenericHandle.m_owner}, set on the fresh clone so a cross-container view can carry
 *       its own owner;</li>
 *   <li><b>a reference to state deliberately shared by all views</b> - {@code m_aFields},
 *       {@code ArrayState}, the freeze cell;</li>
 *   <li><b>an immutable value</b>, i.e. a final field.</li>
 * </ol>
 *
 * <p>Anything else is <b>per-view mutable state</b>, and that is the entire freeze-split bug
 * family: {@code makeImmutable()} through one view left sibling views claiming mutability, and
 * therefore willing to write into frozen shared storage. {@code m_fMutable} was the original
 * instance, now rerouted into the shared {@code FreezeCell}.</p>
 *
 * <p>{@code cloneAs} default-denies mutable handles, so only classes that override
 * {@code supportsMutableViews()} to return true can reach the hazard while mutable. This test
 * enumerates those classes' declared fields and requires each to be final or to appear below with
 * a recorded reason. A new non-final field on an opt-in class fails here, which is the point: the
 * classification is enforced rather than re-derived by whoever next reads the code.</p>
 *
 * <p>See {@code docs/reentrancy/objecthandle-clone-island-resolution.md}.</p>
 */
public class ObjectHandleViewStateTest {
    /**
     * The classes that opt into mutable views, and therefore whose shallow copy must be provably
     * harmless. Keep in sync with the {@code supportsMutableViews()} overrides.
     */
    private static final List<Class<?>> MUTABLE_VIEW_CLASSES = List.of(
            ObjectHandle.GenericHandle.class,
            xArray.ArrayHandle.class,
            xTuple.TupleHandle.class,
            xRTFunction.FunctionHandle.class);

    /**
     * Non-final fields that are classified and allowed, each with the reason it is not per-view
     * mutable state. Anything not listed here must be final.
     */
    private static final Map<String, String> CLASSIFIED_NON_FINAL = Map.of(
            "GenericHandle.m_aFieldOverrides",
                    "shared reference, but overrideField() is copy-on-write: it copies the current "
                    + "array, writes the copy, and rebinds only this view's reference",
            "GenericHandle.m_owner",
                    "per-view identity, not shared state: setOwner() is called exactly once, on the "
                    + "fresh clone inside maskAs(), so a cross-container view carries its own owner",
            "ArrayHandle.m_hHash",
                    "idempotent cache: contents are frozen before a hash is taken, so two views can "
                    + "only ever compute the same value; a shared cell on every array handle would "
                    + "cost more than recomputing at most once per view");

    @Test
    public void mutableViewClassesHaveNoUnclassifiedPerViewState() {
        List<String> unclassified = new ArrayList<>();

        for (Class<?> clz : MUTABLE_VIEW_CLASSES) {
            for (Field field : clz.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                String key = clz.getSimpleName() + "." + field.getName();
                if (!CLASSIFIED_NON_FINAL.containsKey(key)) {
                    unclassified.add(key);
                }
            }
        }

        if (!unclassified.isEmpty()) {
            fail("""
                 Unclassified non-final field(s) on a handle class that opts into mutable views: %s

                 cloneAs() shallow-copies these, so each view gets its own copy - which is the \
                 freeze-split bug shape. Either make the field final, move its state into a cell \
                 shared by all views (see FreezeCell and ArrayState), or add it to \
                 CLASSIFIED_NON_FINAL with the reason it is safe."""
                 .formatted(unclassified));
        }
    }

    /**
     * The classification list must not rot: an entry naming a field that no longer exists means
     * the reasoning it records has been silently orphaned.
     */
    @Test
    public void classificationListHasNoStaleEntries() {
        Set<String> live = new java.util.HashSet<>();
        for (Class<?> clz : MUTABLE_VIEW_CLASSES) {
            for (Field field : clz.getDeclaredFields()) {
                live.add(clz.getSimpleName() + "." + field.getName());
            }
        }

        for (String key : CLASSIFIED_NON_FINAL.keySet()) {
            assertTrue(live.contains(key),
                    "CLASSIFIED_NON_FINAL names a field that no longer exists: " + key
                    + "; remove the entry so the list keeps meaning what it says");
        }
    }

    /**
     * The tuple's element storage is the invariant its own {@code supportsMutableViews()} comment
     * asserts - "never swapped after construction". Pin it: if that reference could be rebound,
     * sibling views would keep the old array and the shared freeze cell would be authoritative
     * over storage nobody reads any more.
     */
    @Test
    public void tupleElementStorageIsFinal() throws NoSuchFieldException {
        Field field = xTuple.TupleHandle.class.getDeclaredField("m_ahValue");
        assertTrue(Modifier.isFinal(field.getModifiers()),
                "TupleHandle.m_ahValue must be final: supportsMutableViews() returns true on the"
                + " stated grounds that this storage is never swapped after construction");
    }
}
