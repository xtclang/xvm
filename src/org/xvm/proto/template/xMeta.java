package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMeta
        extends xObject
    {
    public xMeta(TypeSet types)
        {
        super(types, "x:Meta", "x:Object", Shape.Interface);

        addImplement("x:Referent");
        }

    @Override
    public void initDeclared()
        {
        //    @ro Class class;
        //    @ro Module module;
        //    @ro Struct struct;
        //    Boolean immutable;
        //    @ro Int byteLength;

        ensurePropertyTemplate("class", "x:Class").makeReadOnly();
        ensurePropertyTemplate("module", "x:Module").makeReadOnly();
        ensurePropertyTemplate("struct", "x:Struct").makeReadOnly();
        ensurePropertyTemplate("immutable", "x:Boolean"); // an override of the @ro of the super
        ensurePropertyTemplate("byteLength", "x:Int");
        }
    }
