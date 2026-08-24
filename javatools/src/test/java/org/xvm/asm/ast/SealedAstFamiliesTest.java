package org.xvm.asm.ast;


import java.lang.reflect.Modifier;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ast.BinaryAST.NodeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the sealed BinaryAST tree and its NodeType factory contract (sealed-hierarchy audit,
 * stage 1). The serialized AST is a wire format: before sealing, a NodeType with no class
 * mapping fell into a default arm and surfaced as an UnsupportedOperationException in the middle
 * of module deserialization - the {@code ReturnTStmt} TODO sat invisible inside that default for
 * years. With the tree sealed and the factory exhaustive, adding a NodeType constant without
 * deciding its node class is a compile error at the factory switch, and the tests below keep the
 * documented hole set from growing or being mis-mapped silently.
 */
public class SealedAstFamiliesTest {
    /**
     * The exact, deliberate hole set of the factory. {@code ReturnTStmt} is a real node type
     * with no node class yet; if someone implements it, this test fails and must be updated
     * together with the factory - the hole can no longer be forgotten or half-wired.
     */
    private static final EnumSet<NodeType> UNIMPLEMENTED = EnumSet.of(
            NodeType.ReturnTStmt, NodeType.NotCond, NodeType.NotNullCond,
            NodeType.NotFalseCond, NodeType.MatrixAccessExpr);

    /**
     * Wire-encoding artifacts: never instantiable, encoded specially.
     */
    private static final EnumSet<NodeType> ENCODING_ONLY =
            EnumSet.of(NodeType.Escape, NodeType.RegisterExpr);

    @Test
    public void returnTStmtHoleIsExplicitAndLoud() {
        var error = assertThrows(UnsupportedOperationException.class,
                NodeType.ReturnTStmt::instantiate,
                "ReturnTStmt has no node class yet; the factory must say so loudly");
        assertTrue(error.getMessage().contains("ReturnTStmt"), error.getMessage());
    }

    /**
     * Every NodeType constant is accounted for: instantiable, the null encoding, an encoding
     * artifact, or a listed hole. A new constant cannot land in a silent default arm anymore -
     * the factory switch is exhaustive, so javac refuses it - and this loop refuses a mapping
     * that quietly returns null or the wrong category.
     */
    @Test
    public void everyNodeTypeIsAccountedFor() {
        for (var nodeType : NodeType.values()) {
            if (nodeType == NodeType.None) {
                assertNull(nodeType.instantiate(), "None encodes the absent node");
            } else if (ENCODING_ONLY.contains(nodeType)) {
                assertThrows(IllegalStateException.class, nodeType::instantiate,
                        nodeType + " is a wire-encoding artifact and must never instantiate");
            } else if (UNIMPLEMENTED.contains(nodeType)) {
                var error = assertThrows(UnsupportedOperationException.class,
                        nodeType::instantiate,
                        nodeType + " is a documented hole and must fail loudly");
                assertTrue(error.getMessage().contains(nodeType.name()), error.getMessage());
            } else {
                assertNotNull(nodeType.instantiate(),
                        nodeType + " must map to a node class; a null mapping would corrupt"
                                + " deserialization silently");
            }
        }
    }

    /**
     * The expression wire window: readExprAST can only encode NodeType ordinals 0..31 directly
     * (larger ordinals travel through Escape), and it requires every one of them to be an
     * expression node. An enum reordering that pushes a statement type into the window - the
     * shape the old blind {@code (ExprAST)} cast would have turned into a CCE mid-read - fails
     * here instead.
     */
    @Test
    public void directlyEncodableNodeTypesAreExpressions() {
        for (var nodeType : NodeType.values()) {
            if (nodeType.ordinal() >= 32) {
                continue;
            }
            if (nodeType == NodeType.None || ENCODING_ONLY.contains(nodeType)
                    || UNIMPLEMENTED.contains(nodeType)) {
                continue;
            }
            assertInstanceOf(ExprAST.class, nodeType.instantiate(),
                    nodeType + " sits in the direct expression-encoding window (ordinal "
                            + nodeType.ordinal() + " < 32) and must be an expression node");
        }
    }

    /**
     * The sealed tree itself: both roots sealed, and the whole permitted closure is sealed or
     * final, so no node class can be added or subclassed outside the audited tree.
     */
    @Test
    public void binaryAstTreeIsSealedShut() {
        assertTrue(BinaryAST.class.isSealed(), "BinaryAST must be sealed");
        assertTrue(ExprAST.class.isSealed(), "ExprAST must be sealed");

        Set<Class<?>> seen = new HashSet<>();
        var frontier = new ArrayDeque<Class<?>>();
        frontier.add(BinaryAST.class);
        while (!frontier.isEmpty()) {
            var clz = frontier.poll();
            if (!seen.add(clz)) {
                continue;
            }
            assertTrue(clz.isSealed() || Modifier.isFinal(clz.getModifiers()),
                    clz.getName() + " must be sealed or final; an open node class reopens"
                            + " the wire format");
            if (clz.isSealed()) {
                for (var permitted : clz.getPermittedSubclasses()) {
                    frontier.add(permitted);
                }
            }
        }
        // the root plus its 52 transitive subtypes
        assertEquals(53, seen.size(),
                "the BinaryAST tree drifted; update the sealed-hierarchy audit stage 1");
    }
}
