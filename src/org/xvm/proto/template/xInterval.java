package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

import java.lang.annotation.ElementType;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInterval
        extends xObject
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

        addPropertyTemplate("lowerBound", "ElementType");
        addPropertyTemplate("upperBound", "ElementType");
        addPropertyTemplate("reversed", "x:Boolean");

        addMethodTemplate("contains", new String[]{"ElementType"}, BOOLEAN);
        addMethodTemplate("contains", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        addMethodTemplate("isContainedBy", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        addMethodTemplate("overlaps", new String[]{"x:Interval<ElementType>"}, BOOLEAN);
        addMethodTemplate("intersection", new String[] {"x:Interval<ElementType>"}, new String[] {"x:ConditionalTuple<x:Interval<ElementType>>"});
        addMethodTemplate("union", new String[] {"x:Interval<ElementType>"}, new String[] {"x:ConditionalTuple<x:Interval<ElementType>>"});
        }
    }
