
interface List<ElementType>
    {
    ElementType getElement(Int index);

    List<ElementType> subListStartingWith(Int index);

    Void addAll(List<ElementType> elements);
    }

class SuperCoolList<ElementType>
    {
    ElementType getElement(Int index);

    List<ElementType> subListStartingWith(Int index)
        {
        // I can't return a SuperCoolList to do the sub-list for some reason
        // ...
        return SomeOtherHandyHelperDelegatingList(this, index);
        }

    Void addAll(List<ElementType> elements)
        {
        // ...
        }
    }