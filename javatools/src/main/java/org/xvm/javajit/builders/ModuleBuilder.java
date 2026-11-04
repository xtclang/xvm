package org.xvm.javajit.builders;

import java.lang.constant.ClassDesc;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The builder for Module types.
 */
public class ModuleBuilder
        extends CommonBuilder {

    public ModuleBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
    }

    @Override
    public ClassDesc getSuperCD() {
        return CD_nModule;
    }
}
