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
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Referent");

        //    @ro Class class;
        //    @ro Module module;
        //    @ro Struct struct;
        //    Boolean immutable;
        //    @ro Int byteLength;

        addPropertyTemplate("class", "x:Class").makeReadOnly();
        addPropertyTemplate("module", "x:Module").makeReadOnly();
        addPropertyTemplate("struct", "x:Struct").makeReadOnly();
        addPropertyTemplate("immutable", "x:Boolean"); // an override of the @ro of the super
        addPropertyTemplate("byteLength", "x:Int");
        }
    }
