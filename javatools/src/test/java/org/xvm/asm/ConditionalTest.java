package org.xvm.asm;


import java.io.IOException;

import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.xvm.asm.constants.AllCondition;
import org.xvm.asm.constants.AnyCondition;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ConditionalConstant.Influence;
import org.xvm.asm.constants.NamedCondition;
import org.xvm.asm.constants.VersionConstant;
import org.xvm.asm.constants.VersionedCondition;


/**
 * Tests for ConditionalConstant and related structures.
 */
public class ConditionalTest
    {
//    @Test
    public void testSimple()
            throws IOException
        {
        FileStructure       file   = new FileStructure("test");
        ConstantPool        pool   = file.getConstantPool();
        NamedCondition      condX  = pool.ensureNamedCondition("X");
        NamedCondition      condY  = pool.ensureNamedCondition("Y");
        AllCondition        condB  = pool.ensureAllCondition(condX, condY);
        AnyCondition        condE  = pool.ensureAnyCondition(condX, condY);

        Assert.assertTrue(!condX.isTerminalInfluenceBruteForce());
        Assert.assertTrue(!condY.isTerminalInfluenceBruteForce());
        Assert.assertTrue(!condB.isTerminalInfluenceBruteForce());
        Assert.assertTrue( condE.isTerminalInfluenceBruteForce());

        Set<ConditionalConstant> setX = condX.terminals();
        Set<ConditionalConstant> setY = condY.terminals();
        Set<ConditionalConstant> setB = condB.terminals();
        Set<ConditionalConstant> setE = condE.terminals();

        Assert.assertTrue(setX.size() == 1 && setX.contains(condX));
        Assert.assertTrue(setY.size() == 1 && setY.contains(condY));
        Assert.assertTrue(setB.size() == 2 && setB.contains(condX) && setB.contains(condY));
        Assert.assertTrue(setE.size() == 2 && setE.contains(condX) && setE.contains(condY));

        Map<ConditionalConstant, Influence> mapX = condX.terminalInfluences();
        Map<ConditionalConstant, Influence> mapY = condY.terminalInfluences();
        Map<ConditionalConstant, Influence> mapB = condB.terminalInfluences();
        Map<ConditionalConstant, Influence> mapE = condE.terminalInfluences();

        Assert.assertTrue(mapX.size() == 1);
        Assert.assertEquals(mapX.get(condX), Influence.IDENTITY);

        Assert.assertTrue(mapY.size() == 1);
        Assert.assertEquals(mapY.get(condY), Influence.IDENTITY);

        Assert.assertTrue(mapB.size() == 2);
        Assert.assertEquals(mapB.get(condX), Influence.AND);
        Assert.assertEquals(mapB.get(condY), Influence.AND);

        Assert.assertTrue(mapE.size() == 2);
        Assert.assertEquals(mapE.get(condX), Influence.OR);
        Assert.assertEquals(mapE.get(condY), Influence.OR);
        }

//    @Test
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
        MethodStructure     method = clz.createMethod(false, Constants.Access.PUBLIC, null, new Parameter[]{}, "foo", new Parameter[]{},
                true, true);

        // module is both v1 and v2
        module.setCondition(condVB);

        // class is v1
        clz.setCondition(condV1);

        // package is v2
        pkg.setCondition(condV2);

        // method is both
        method.setCondition(condVB);

        FileStructureTest.testFileStructure(file);

        Assert.assertTrue(pkg.getChild("foo") == clz.getChild("foo"));
        Assert.assertTrue(pkg.getChild("foo") instanceof MultiMethodStructure);

        Assert.assertTrue(method.getParent() instanceof MultiMethodStructure);
        Assert.assertTrue(method.getParent().getParent() instanceof CompositeComponent);
        }
    }