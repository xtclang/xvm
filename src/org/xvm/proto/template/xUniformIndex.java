package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xUniformIndex
        extends xObject
    {
    public xUniformIndex(TypeSet types)
        {
        super(types, "x:UniformIndex<IndexType,ElementType>", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    @op ElementType get(IndexType index);
        //    @op Void set(IndexType index, ElementType value)
        //    @op Ref<ElementType> elementAt(IndexType index)

        addMethodTemplate("get", new String[]{"IndexType"}, new String[]{"ElementType"});
        addMethodTemplate("set", new String[]{"IndexType", "ElementType"}, VOID);
        addMethodTemplate("elementAt", new String[]{"IndexType"}, new String[]{"x:Ref<ElementType>"});
        }
    }
