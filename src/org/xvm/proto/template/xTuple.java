package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xTuple
        extends xObject
    {
    public xTuple(TypeSet types)
        {
        // deferring the variable length generics <ElementType...>
        super(types, "x:Tuple", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
// TODO deferred for now
//        addImplement("FixedSizeAble");
//        addImplement("PersistentAble");
//        addImplement("ConstAble");

        //    @ro Int size;
        //    @op ElementTypes[index] get(Int index);
        //    @op Void set(Int index, ElementTypes[index] newValue);
        //    @op Ref<ElementTypes[index]> elementAt(Int index);
        //    @op Tuple add(Tuple that);
        //    Tuple<ElementTypes> replace(Int index, ElementTypes[index] value);
        //    @op Tuple slice(Range<Int> range);
        //    Tuple remove(Int index);
        //    Tuple remove(Range<Int> range);
        // TODO deferred
        //    Tuple<ElementTypes> ensureFixedSize();
        //    Tuple<ElementTypes> ensurePersistent();
        //    Tuple<ElementTypes> ensureConst();

        addPropertyTemplate("size", "x:Int").makeReadOnly();
        addMethodTemplate("get", INT, new String[]{"x:Type"}); // not quite right
        addMethodTemplate("set", new String[]{"x:Int", "x:Type"}, VOID); // not quite right
        addMethodTemplate("elementAt", INT, new String[]{"x:Ref<x:Type>"}); // not quite right
        addMethodTemplate("add", new String[] {"x:Tuple"}, new String[] {"x:Tuple"}); // non "virtual"
        addMethodTemplate("replace", new String[] {"x:Int", "x:Type"}, THIS); // not quite right
        addMethodTemplate("slice", new String[] {"x:Range<x:Int>"}, new String[] {"x:Tuple"}); // non "virtual"
        addMethodTemplate("remove", INT, new String[] {"x:Tuple"}); // non "virtual"
        addMethodTemplate("remove", new String[] {"x:Range<x:Int>"}, new String[] {"x:Tuple"}); // non "virtual"

        }
    }
