package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInterval
        extends TypeCompositionTemplate
    {
    public xInterval(TypeSet types)
        {
        super(types, "x:Interval<ElementType>", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        // construct

        //    ElementType lowerBound;
        //    ElementType upperBound;
        //    Boolean reversed;
        //    Boolean contains(ElementType value)
        //    Boolean contains(Interval<ElementType> that)
        //    Boolean isContainedBy(Interval<ElementType> that)
        //    Boolean overlaps(Interval<ElementType> that)
        //    conditional Interval<ElementType> intersection(Interval<ElementType> that)
        //    conditional Interval<ElementType> union(Interval<ElementType> that)

        ensurePropertyTemplate("lowerBound", "ElementType");
        ensurePropertyTemplate("upperBound", "ElementType");
        ensurePropertyTemplate("reversed", "x:Boolean");

        ensureMethodTemplate("contains", new String[]{"ElementType"}, BOOLEAN);
        ensureMethodTemplate("contains", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        ensureMethodTemplate("isContainedBy", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        ensureMethodTemplate("overlaps", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        ensureMethodTemplate("intersection", new String[]{"x:Interval<ElementType>"}, new String[]{"x:ConditionalTuple<x:Interval<ElementType>>"});
        ensureMethodTemplate("union", new String[]{"x:Interval<ElementType>"}, new String[]{"x:ConditionalTuple<x:Interval<ElementType>>"});
        }
    }
