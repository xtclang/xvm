/**
 * An array is a container of elements of a particular type.
 */
class Array<ElementType>
        implements Sequence<ElementType>
    {
    construct Array(Int capacity)
        {
        if (capacity < 0)
            {
            throw new IllegalArgument("capacity", capacity, "must be >= 0");
            }
        this.capacity = capacity;
        }

    construct (Int capacity, function ElementType fn())
        {
        construct Array(capacity);

        Element<ElementType>? head = null;
        if (capacity > 0)
            {
            head = new Element<ElementType>(fn());

            Element<ElementType> tail = head;
            for (Int i : 1..capacity)
                {
                Element<ElementType> node = new Element<ElementType>(fn());
                tail.next = node;
                tail      = node;
                }
            }

        this.head     = head;
        this.capacity = capacity;
        this.length   = capacity;
        }

    public/private Int capacity = 0;
    public/private Int length   = 0;

    Ref<ElementType> elementAt(Int index)
        {
        if (index < 0 || index >= length)
            {
            throw new IllegalArrayIndex(index, 0, length);
            }

        Element element = (Element) head;
        while (index-- > 0)
            {
            element = (Element) element.next;
            }

        return element;
        }

    private Element<ElementType>? head;

    class Element<RefType>(ElementType value)
            extends Ref<RefType>
        {
        ElementType get()
            {
            return value;
            }

        void set(ElementType value)
            {
            this.value = value;
            }

        Element<RefType>? next;
        }
    }
