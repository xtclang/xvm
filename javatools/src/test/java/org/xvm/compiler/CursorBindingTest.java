package org.xvm.compiler;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Register;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grouped call facts preserve scope, inspection state and the existing flat-record contract. */
public class CursorBindingTest {
    @Test
    public void groupedConstructionMatchesTheExistingRecord() {
        var original = fixture();
        var grouped  = new CursorBinding(original.variables(), original.thisType(), original.instance(),
                original.types(), original.callFacts());

        assertEquals(original, grouped);
        assertEquals(original.candidates(), grouped.callFacts().candidates());
        assertEquals(original.functions(), grouped.callFacts().functions());
        assertEquals(original.argumentValues(), grouped.callFacts().argumentValues());
        assertEquals(original.argumentProperties(), grouped.callFacts().argumentProperties());
    }

    @Test
    public void independentCallUpdatesRetainEveryOtherFactAndLeaveTheOriginalAlone() {
        var expected = fixture();
        var scope    = new CursorBinding(expected.variables(), expected.thisType(), expected.instance())
                .withTypes(expected.types());
        var updated  = scope.withCandidates(expected.candidates())
                .withFunctions(expected.functions())
                .withArgumentValues(expected.argumentValues())
                .withArgumentProperties(expected.argumentProperties());

        assertEquals(expected, updated);
        assertFalse(scope.callsInspected());
        assertTrue(scope.candidates().isEmpty());
        assertTrue(scope.functions().isEmpty());
        assertTrue(scope.argumentValues().isEmpty());
        assertTrue(scope.argumentProperties().isEmpty());
        assertEquals(updated.callFacts(), updated.withTypes(List.of()).callFacts());
    }

    @Test
    public void argumentUpdatesDoNotTurnUninspectedCallsIntoRejectedCalls() {
        var expected = fixture();
        var scope    = new CursorBinding(expected.variables(), expected.thisType(), expected.instance());
        var values   = scope.withArgumentValues(expected.argumentValues())
                .withArgumentProperties(expected.argumentProperties());

        assertFalse(values.callsInspected());
        assertFalse(values.withTypes(expected.types()).callsInspected());
        assertTrue(values.withCandidates(List.of()).callsInspected());
        assertTrue(values.withFunctions(List.of()).callsInspected());
        assertEquals(values.argumentProperties(), values.withCandidates(List.of()).argumentProperties());
    }

    @Test
    public void groupedListsAreDetachedAndImmutable() {
        var expected   = fixture().callFacts();
        var candidates = new ArrayList<>(expected.candidates());
        var functions  = new ArrayList<>(expected.functions());
        var values     = new ArrayList<>(expected.argumentValues());
        var properties = new ArrayList<>(expected.argumentProperties());
        var facts      = new CursorBinding.CallFacts(candidates, true, functions, values, properties);

        List.of(candidates, functions, values, properties).forEach(List::clear);
        assertEquals(expected, facts);
        List.of(facts.candidates(), facts.functions(), facts.argumentValues(), facts.argumentProperties())
                .forEach(list -> assertThrows(UnsupportedOperationException.class, list::clear));
    }

    private static CursorBinding fixture() {
        var file      = new FileStructure("CursorFacts");
        var pool      = file.getConstantPool();
        var identity  = file.getModule().getIdentityConstant();
        var type      = identity.getType();
        var signature = pool.ensureSignatureConstant("call", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var method    = pool.ensureMethodConstant(identity, signature);
        var variable  = new CursorBinding.Variable("value", new Register(type, "value", 0), type, true);
        var candidate = new CursorBinding.Candidate(method, signature, List.of(), false);
        var function  = new CursorBinding.FunctionCandidate(pool.buildFunctionType(ConstantPool.NO_TYPES), List.of());
        var property  = new CursorBinding.Property("property", pool.ensurePropertyConstant(identity, "property"), type);
        return new CursorBinding(List.of(variable), type, true, List.of(new CursorBinding.NamedType("CursorFacts", identity)),
                List.of(candidate), true, List.of(function), List.of(variable), List.of(property));
    }
}
