package org.xvm.asm;


import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.AnyCondition;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ConditionalConstant.Influence;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.VersionedCondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ConditionalConstant and related structures.
 */
public class ConditionalTest
    {
    @Test @Disabled
    public void testSimple()
        {
        FileStructure       file   = new FileStructure("test");
        ConstantPool        pool   = file.getConstantPool();
        NamedCondition      condX  = pool.ensureNamedCondition("X");
        NamedCondition      condY  = pool.ensureNamedCondition("Y");
        AllCondition        condB  = pool.ensureAllCondition(condX, condY);
        AnyCondition        condE  = pool.ensureAnyCondition(condX, condY);

        assertFalse(condX.isTerminalInfluenceBruteForce());
        assertFalse(condY.isTerminalInfluenceBruteForce());
        assertFalse(condB.isTerminalInfluenceBruteForce());
        assertTrue(condE.isTerminalInfluenceBruteForce());

        Set<ConditionalConstant> setX = condX.terminals();
        Set<ConditionalConstant> setY = condY.terminals();
        Set<ConditionalConstant> setB = condB.terminals();
        Set<ConditionalConstant> setE = condE.terminals();

        assertTrue(setX.size() == 1 && setX.contains(condX));
        assertTrue(setY.size() == 1 && setY.contains(condY));
        assertTrue(setB.size() == 2 && setB.contains(condX) && setB.contains(condY));
        assertTrue(setE.size() == 2 && setE.contains(condX) && setE.contains(condY));

        Map<ConditionalConstant, Influence> mapX = condX.terminalInfluences();
        Map<ConditionalConstant, Influence> mapY = condY.terminalInfluences();
        Map<ConditionalConstant, Influence> mapB = condB.terminalInfluences();
        Map<ConditionalConstant, Influence> mapE = condE.terminalInfluences();

        assertEquals(1, mapX.size());
        assertEquals(mapX.get(condX), Influence.IDENTITY);

        assertEquals(1, mapY.size());
        assertEquals(mapY.get(condY), Influence.IDENTITY);

        assertEquals(2, mapB.size());
        assertEquals(mapB.get(condX), Influence.AND);
        assertEquals(mapB.get(condY), Influence.AND);

        assertEquals(2, mapE.size());
        assertEquals(mapE.get(condX), Influence.OR);
        assertEquals(mapE.get(condY), Influence.OR);
        }

    @Test @Disabled
    public void testDiamond()
            throws IOException
        {
        FileStructure       file   = new FileStructure("test");
        ConstantPool        pool   = file.getConstantPool();
        VersionConstant     v1     = pool.ensureVersionConstant(new Version("1"));
        VersionConstant     v2     = pool.ensureVersionConstant(new Version("2"));
        VersionedCondition  condV1 = pool.ensureVersionedCondition(v1);
        VersionedCondition  condV2 = pool.ensureVersionedCondition(v2);
        AnyCondition        condVB = pool.ensureAnyCondition(condV1, condV2);
        ModuleStructure     module = file.getModule();
        ClassStructure      clz    = module.createClass(Constants.Access.PUBLIC, Component.Format.CLASS, "Util", null);
        PackageStructure    pkg    = module.createPackage(Constants.Access.PUBLIC, "Util", null);
        MethodStructure     method = clz.createMethod(false, Constants.Access.PUBLIC, null,
                                        Parameter.NO_PARAMS, "foo", Parameter.NO_PARAMS, true, true);

        // module is both v1 and v2
        module.setCondition(condVB);

        // class is v1
        clz.setCondition(condV1);

        // package is v2
        pkg.setCondition(condV2);

        // method is both
        method.setCondition(condVB);

        FileStructureTest.testFileStructure(file);

        assertSame(pkg.getChild("foo"), clz.getChild("foo"));
        assertTrue(pkg.getChild("foo") instanceof MultiMethodStructure);

        assertTrue(method.getParent() instanceof MultiMethodStructure);
        assertTrue(method.getParent().getParent() instanceof CompositeComponent);
        }
    }
