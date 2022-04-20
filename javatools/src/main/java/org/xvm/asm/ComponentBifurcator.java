package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.constants.ConditionalConstant;


/**
 * A ComponentBifurcator is a condition-aware and sibling-aware helper that splits a component as
 * necessary to respond to the request for various conditional views of the component.
 */
public class ComponentBifurcator
    {
    public ComponentBifurcator(Component component)
        {
        // this could support CompositeComponent, but it doesn't
        assert !(component instanceof CompositeComponent);

        this.unsplit = component;
        }

    /**
     * Obtain a component that represents the set of components resulting from bifurcation that
     * could potentially satisfy the specified condition. If more than one component could satisfy
     * the condition, then the result will be a CompositeComponent.
     *
     * @param cond  the condition to satisfy
     *
     * @return a component representing all of the components that match the specified condition
     */
    public Component getComponent(ConditionalConstant cond)
        {
        if (splitter == null)
            {
            return cond == null ? unsplit : split(cond);
            }

        List<Component> list = new ArrayList<>();
        collectMatchingComponents(cond, list);
        if (list.isEmpty())
            {
            throw new IllegalStateException();
            }
        else if (list.size() == 1)
            {
            return list.get(0);
            }
        else
            {
            return new CompositeComponent(unsplit.getParent(), list);
            }
        }

    /**
     * Find all of the components resulting from bifurcation that could potentially satisfy the
     * specified condition.
     *
     * @param cond  the condition to satisfy
     * @param list  the list to place matching components into
     */
    public void collectMatchingComponents(ConditionalConstant cond, List<Component> list)
        {
        if (splitter == null)
            {
            list.add(cond == null ? unsplit : split(cond));
            }
        else if (cond == null)
            {
            iftrue.collectMatchingComponents(null, list);
            iffalse.collectMatchingComponents(null, list);
            }
        else
            {
            // determine if the condition is composed at least partially of this bifurcator's
            // splitting condition
            ConditionalConstant.Bifurcation plan = splitter.bifurcate(cond);
            if (plan.isTruePossible())
                {
                iftrue.collectMatchingComponents(plan.getTrueCondition(), list);
                }
            if (plan.isFalsePossible())
                {
                iffalse.collectMatchingComponents(plan.getFalseCondition(), list);
                }
            }
        }

    /**
     * Split the unsplit component into two components, based on the specified condition.
     *
     * @param cond  the condition to use to split the component
     *
     * @return the component corresponding to the <b>true</b> branch of the condition
     */
    private Component split(ConditionalConstant cond)
        {
        Component componentTrue  = unsplit;
        Component componentFalse = unsplit.cloneBody(); // REVIEW w.r.t. changes to clone() etc.
        // TODO link componentFalse as a sibling from componentTrue?

        componentTrue.addAndCondition(cond);
        componentFalse.addAndCondition(cond.negate());

        splitter = cond;
        iftrue   = new ComponentBifurcator(componentTrue);
        iffalse  = new ComponentBifurcator(componentFalse);

        return componentTrue;
        }

    private final Component     unsplit;
    private ConditionalConstant splitter;
    private ComponentBifurcator iftrue;
    private ComponentBifurcator iffalse;
    }