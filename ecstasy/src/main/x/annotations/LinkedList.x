import reflect.Argument;
import reflect.Annotation;

/**
 * The LinkedList annotation is used to turn a property into a linked list, **without using any
 * additional memory** beyond the property or properties that represent the next and previous
 * pointers.
 *
 * There are three dimensions that define a total of six distinct scenarios supported by this mixin:
 *
 * 1. Whether the list is singly linked or bi-directionally linked, with the list being considered
 *    bidirectionally linked iff the previous element property is specified as an annotation
 *    argument;
 * 2. Whether or not the type C of the containing class (the class that contains the annotated
 *    property) is itself the Element type (which would allow the containing "this" to possibly be
 *    a member of the list);
 * 3. Whether the containing "this" is explicitly omitted or not (which only matters if the type of
 *    the containing class is the Element type).
 *
 * The six supported scenarios are:
 *
 * 1. `C.is(Element)`, singly linked, omitted is not specified: the containing "this" is the head of
 *    the list, and the "next" property (if not specified) defaults to the annotated property;
 * 2. `C.is(Element)`, singly linked, omitted is specified: the annotated property contains the head
 *    of the list, the "next" property (if not specified) defaults to the annotated property;
 * 3. `C.is(Element)`, doubly linked, omitted is not specified: the containing "this" is in the list
 *    iff the annotated property is also the "next" property (and the "next" property defaults to
 *    the annotated property);
 * 4. `C.is(Element)`, doubly linked, omitted is specified: the annotated property contains an
 *    element of the doubly-linked list (not necessarily the head);
 * 5. `!C.is(Element)`, singly linked, omitted is assumed: the head is in the annotated property,
 *    and the "next" property must be specified
 * 6. `!C.is(Element)`, doubly linked, omitted is assumed: the annotated property contains an
 *    element of the doubly-linked list (not necessarily the head), and both the "next" and "prev"
 *    properties must be specified.
 *
 * Here are examples of some of these scenarios; note that the properties of type [List] are only
 * helpers to illustrate how one obtains the `LinkedList` instance by obtaining a reference to the
 * property's [Var]:
 *
 *     class Phone
 *         {
 *         String desc;
 *         String number;
 *
 *         // scenario #1 - all phone numbers in the list, starting with this one
 *         @LinkedList Phone? next;
 *         List<Phone> list.get()
 *             {
 *             return &next;
 *             }
 *         }
 *
 *     class Person
 *         {
 *         String  name;
 *         Date    dob;
 *
 *         // scenario #2 - ancestors, starting with this person's parent (assume asexual reproduction,
 *         // since this is linked list structure, instead of a tree)
 *         @LinkedList(omitThis=True) Person? parent;
 *         List<Person> ancestors.get()
 *             {
 *             return &parent;
 *             }
 *
 *         // scenario #3 - all siblings, including this person
 *         @LinkedList(prev=prevSibling) Person? nextSibling;
 *         Person? prevSibling;
 *         List<Person> siblings.get()
 *             {
 *             return &nextSibling;
 *             }
 *
 *         // scenario #4 - all children of this person
 *         @LinkedList(next=nextSibling, prev=prevSibling, omitThis=True) Person? child;
 *         List<Person> children.get()
 *             {
 *             return &child;
 *             }
 *
 *         // scenario #5 - all phone numbers of this person
 *         @LinkedList(Phone.next) Phone? phone; // scenario #5
 *         List<Phone> phoneNumbers.get()
 *             {
 *             return &phone;
 *             }
 *         }
 */
mixin LinkedList<Element>
        into Var<Element?>
        implements List<Element>
    {
    /**
     * This `Link` type is the property type that will provide a next pointer from each element to
     * each next element, and optionally from each element to its previous element.
     *
     * The LinkedList annotation is a Var annotation designed to be used on a property that
     * represents either a "list head" pointer or a "next element" pointer. The elements in the list
     * must have a "next element" pointer (which could be the same property that this mixin is
     * annotated on), and _may_ have a "previous element" pointer (i.e. iff the list is
     * bidirectional).
     *
     * This mixin implementation needs to be able to assume that the property is on each element,
     * that the property returns an Element or `Null`, and that the property is settable (a `Var`).
     * The `Link` type is a simple summary of those requirements.
     *
     * Note that this implementation requires the property to be a read/write property, even if the
     * list is read-only.
     */
    typedef Property<Element, Element?, Var<Element?>> Link;


    // ----- constructor ---------------------------------------------------------------------------

    /**
     * Configure the LinkedList.
     *
     * The LinkedList is stateless, so the constructor simply validates the mixin arguments. The
     * arguments of a `Ref`/`Var` annotation are available at runtime from the [Ref] interface;
     * since annotation arguments must be constant, the use of the annotation does not need to
     * introduce any additional runtime storage requirement to provide the LinkedList interface,
     * other than the space already being allocated for the property itself.
     *
     * @param next      the property that points to the next item in the list; defaults to this
     *                  property
     * @param prev      if the LinkedList is bi-directional, this specifies the property that
     *                  points to the previous item in the list; otherwise Null
     * @param omitThis  specify True to omit the parent (i.e. object-that-contains-this-property) in
     *                  the List, if it would otherwise be assumed that the parent would be the head
     *                  of the List (i.e. in a singly linked list with the parent is itself an Element)
     * @param readOnly  specify True to prevent mutation via the List interface
     */
    construct(Link?   next     = Null,
              Link?   prev     = Null,
              Boolean omitThis = False,
              Boolean readOnly = False)
        {
        // the property that this is mixed into must be of the element type (but can be Null).
        assert Referent == Element?;
        }
    finally
        {
        // these checks are deferred until the construction is completing, to ensure that a "this"
        // exists for the property
        assert (Property prop, val container) := isProperty();

        // the property must be of the type `Element?` (Null or an Element)
        assert prop.Referent == Element?;

        // if the container type is not an element, then it can't be the head of the list and this
        // property can't be the implicit "next", so verify that either the container could be the
        // head of the list (by being the Element type), or that a "next" is specified
        assert next != Null || container.is(Element);
        }


    // ----- metadata ------------------------------------------------------------------------------

    /**
     * Obtain the constructor arguments for this LinkedList.
     *
     * @return the array of Arguments
     */
    protected Argument[] annotationArgs.get()
        {
        for (Annotation anno : annotations)
            {
            val clz = anno.mixinClass;
            if (clz == LinkedList || clz.derivesFrom(LinkedList))
                {
                return anno.arguments;
                }
            }
        assert;
        }

    /**
     * Obtain the configuration for this LinkedList.
     *
     * @param rewind  if the list is bi-directional and "this node" is not the start of the list,
     *                then force the rewind it to the actual head of the list
     *
     * @return head         the first element in the list if the list is uni-directional; if the
     *                      list is bi-directional, then this _may_ not be the first element in
     *                      the list (if `rewind == false`); in either case, the element may be
     *                      Null
     * @return nextLink     the property of each element that provides the following element
     * @return prevLink     the optional property of each element that provides the previous
     *                      element; otherwise Null
     * @return thisHeadVar  True iff `this` Ref/Var points to the head of the list, allowing the
     *                      head to be removed, replaced, etc. (if the list is not read-only)
     * @return readOnly     True iff the list has been specified as being read-only, which means
     *                      that no mutations are permitted via the [List] interface
     */
    protected (Element? head, Link nextLink, Link? prevLink, Boolean thisHeadVar, Boolean readOnly)
            readConfig(Boolean rewind = True)
        {
        Argument[] args     = annotationArgs;
        Link?      nextLink = args[0].value.as(Link?);
        Link?      prevLink = args[1].value.as(Link?);
        Boolean    omit     = args[2].value.as(Boolean);
        Boolean    ro       = args[3].value.as(Boolean);

        // "this" ref is from a property inside some parent object
        assert (Property<Object, Element?, LinkedList> prop, Object parent) := isProperty();

        // if the "nextLink" property isn't specified, then default to using the property for this ref
        nextLink ?:= prop.as(Link);

        // the head is either the "this" that contains this ref, or it's the value inside this ref;
        // scenarios #1 and #3 have no head var, because the head is the parent, while the rest have
        // a head var because either the parent is omitted, or the parent is the wrong type to even
        // be in the list
        Boolean  thisHeadVar = omit || !parent.is(Element);
        Element? head        = thisHeadVar ? get() : parent.as(Element);

        // for any bi-directional list (scenarios #3, #4, and #6), the assumed head may have a
        // previous node, so rewind to the beginning of the list if necessary
        if (rewind && head != Null && prevLink != Null)
            {
            Boolean changed = False;
            while (Element preceding ?= prevLink.get(head))
                {
                head    = preceding;
                changed = True;
                }

            // update the list head if it changed
            if (changed && !ro && thisHeadVar)
                {
                set(head);
                }
            }

        return (head, nextLink, prevLink, thisHeadVar, ro);
        }

    /**
     * Obtain the configuration for reading and mutating this LinkedList.
     *
     * @param rewind        if the list is bi-directional and "this node" is not the start of the
     *                      list, then force the rewind it to the actual head of the list
     *
     * @return head         the first element in the list, which may be Null
     * @return nextLink     the property of each element that provides the following element
     * @return prevLink     the optional property of each element that provides the previous
     *                      element; otherwise Null
     * @return thisHeadVar  True iff `this` Ref/Var points to the head of the list, allowing the
     *                      head to be removed, replaced, etc. (if the list is not read-only)
     *
     * @throws ReadOnly  if the list has been configured as read-only
     */
    protected (Element? head, Link nextLink, Link? prevLink, Boolean thisHeadVar)
            writeConfig(Boolean rewind = True)
        {
        (val head, val nextLink, val prevLink, val headVar, val readOnly) = readConfig(rewind);
        if (readOnly)
            {
            throw new ReadOnly();
            }
        return head, nextLink, prevLink, headVar;
        }


    // ----- List operations -----------------------------------------------------------------------

    @Override
    @RO Boolean indexed.get()
        {
        return False;
        }

    @Override
    conditional Int knownSize()
        {
        return False;
        }

    @Override
    Boolean empty.get()
        {
        Element? head = readConfig(rewind = False);
        return head != Null;
        }

    @Override
    Int size.get()
        {
        Int count = 0;
        for ((Element? node, Link nextLink) = readConfig();
                node != Null;
                node = nextLink.get(node))
            {
            ++count;
            }
        return count;
        }

    @Override
    Iterator<Element> iterator()
        {
        return new Iterator()
            {
            construct()
                {
                (node, nextLink) = readConfig();
                }

            private Element? node;
            private Link     nextLink;

            @Override
            conditional Element next()
                {
                Element? cur = node;
                if (cur != Null)
                    {
                    node = nextLink.get(cur);
                    return True, cur;
                    }
                return False;
                }
            };
        }

    @Override
    conditional Element first()
        {
        Element? head = readConfig();
        return head == Null
                ? False
                : (True, head);
        }

    @Override
    conditional Element last()
        {
        (Element? node, Link nextLink) = readConfig(rewind = False);
        if (node == Null)
            {
            return False;
            }

        Element tail;
        do
            {
            tail = node;
            node = nextLink.get(node);
            }
        while (node != Null);
        return True, tail;
        }

    @Override
    conditional Int indexOf(Element value, Int startAt = 0)
        {
        Loop: for ((Element? node, Link nextLink) = readConfig();
                node != Null;
                node = nextLink.get(node))
            {
            if (Loop.count >= startAt && node == value)
                {
                return True, Loop.count;
                }
            }
        return False;
        }

    @Override
    @Op("[]") Element getElement(Int index)
        {
        Loop: for ((Element? node, Link nextLink) = readConfig();
                node != Null;
                node = nextLink.get(node))
            {
            if (index == Loop.count)
                {
                return node;
                }
            }
        throw new OutOfBounds($"index={index}");
        }

    @Override
    @Op("[..]") List<Element> slice(Range<Int> indexes)
        {
        TODO check interval descending, and flip it if so, then collect forwards in an array, then flip again if descending
        // TODO
        }

    @Override
    @Op("[]=") void setElement(Int index, Element value)
        {
        assert:arg value != Null;

        (Element? node, Link nextLink, Link? prevLink, Boolean thisHeadVar) = writeConfig();

        // verify that the value isn't already linked in to some list
        assert:arg nextLink.get(value) == Null && prevLink?.get(value) == Null;

        if (prevLink == Null)
            {
            // drag the nodeVar behind the node, because in a singly linked list we need to keep a
            // reference to the node that will point to the newly inserted node
            Loop: for (Var<Element?> nodeVar = this;
                    node != Null;
                    nodeVar = nextLink.of(node), node = nodeVar.get())
                {
                if (index == Loop.count)
                    {
                    // scenario #1 cannot replace the head, because the parent "this" _is_ the head
                    if (index == 0 && !thisHeadVar)
                        {
                        throw new ReadOnly("LinkedList head cannot be modified");
                        }

                    // replace "node" with "value" by linking the prev to the value to the next
                    Element? next = nextLink.get(node);
                    nodeVar.set(value);     // note: this updates the head var if index==0
                    nextLink.set(value, next?);

                    // unlink the old node (don't let it think it's still in this list)
                    nextLink.set(node, Null);

                    return;
                    }
                }
            }
        else
            {
            Loop: for ( ; node != Null; node = nextLink.get(node))
                {
                if (index == Loop.count)
                    {
                    if (index > 0)
                        {
                        // replace "node" with "value" by linking the prev to the value to the next
                        assert Element prev ?= prevLink.get(node);
                        nextLink.set(prev, value);
                        prevLink.set(value, prev);
                        }
                    Element? next = nextLink.get(node);
                    if (next != Null)
                        {
                        prevLink.set(next, value);
                        nextLink.set(value, next);
                        }

                    // unlink the old node (don't let it think it's still in this list)
                    prevLink.set(node, Null);
                    nextLink.set(node, Null);

                    // update the head var
                    if (index == 0 && thisHeadVar)
                        {
                        set(value);
                        }

                    return;
                    }
                }
            }

        throw new OutOfBounds($"index={index}");
        }

    @Override
    @Op("+") LinkedList add(Element value)
        {
        (Element? node, Link nextLink, Link? prevLink, Boolean thisHeadVar) = writeConfig();

        // verify that the value isn't already linked in to some list
        assert:arg nextLink.get(value) == Null && prevLink?.get(value) == Null;

        if (node == Null)
            {
            assert thisHeadVar;
            set(value);
            return this;
            }

        // fast-forward to the end
        Element tail;
        do
            {
            tail = node;
            node = nextLink.get(node);
            }
        while (node != Null);

        nextLink.set(tail, value);
        prevLink?.set(value, tail);
        return this;
        }

    @Override
    LinkedList addAll(Iterator<Element> iter)
        {
        (Element? node, Link nextLink, Link? prevLink, Boolean thisHeadVar) = writeConfig();

        // fast-forward to the end
        Element? tail = Null;
        while (node != Null)
            {
            tail = node;
            node = nextLink.get(node);
            }

        // gluing a linked list to a linked list is easy; it's like adding one value
        if (iter.is(Inner))
            {
            Object that = iter.outer;
            if (that.is(LinkedList),
                    Property<Object> thisProp := this.isProperty(),
                    Property<Object> thatProp := that.isProperty(),
                    &thisProp == &thatProp)
                {
                Element? thatNode = that.writeConfig();
                if (thatNode != Null)
                    {
                    // adding the list means adding the whole list; since it's linked, we can't just
                    // add part of it without destroying the original
                    assert:arg Element firstToAdd := iter.next(), &thatNode == &firstToAdd;

                    // obviously, it can't be linked to something else before it
                    assert:arg prevLink?.get(thatNode) == Null;

                    if (tail == Null)
                        {
                        set(thatNode);
                        }
                    else
                        {
                        nextLink.set(tail, thatNode);
                        prevLink?.set(thatNode, tail);
                        }
                    }
                return this;
                }
            }

        // append one at a time by (i) taking it from the iterator, (ii) linking it to the tail, and
        // (iii) advancing the tail
        for (Element value : iter)
            {
            // verify that the value isn't already linked in to some list
            assert:arg nextLink.get(value) == Null && prevLink?.get(value) == Null;

            if (tail == Null)
                {
                set(value);
                }
            else
                {
                nextLink.set(tail, value);
                prevLink?.set(value, tail);
                }

            tail = value;
            }

        return this;
        }

    @Override
    @Op("-") LinkedList remove(Element value)
        {
        removeIfPresent(value);
        return this;
        }

    @Override
    conditional LinkedList removeIfPresent(Element value)
        {
        (Element? node, Link nextLink, Link? prevLink, Boolean thisHeadVar) = writeConfig();

        if (prevLink == Null)
            {
            // drag the nodeVar behind the node, because in a singly linked list we need to keep a
            // reference to the node that points to the node to remove
            Loop: for (Var<Element?> nodeVar = this;
                    node != Null;
                    nodeVar = nextLink.of(node), node = nodeVar.get())
                {
                if (node == value)
                    {
                    // scenario #1 cannot remove the head, because the parent "this" _is_ the head
                    if (Loop.count == 0 && !thisHeadVar)
                        {
                        throw new ReadOnly("LinkedList head cannot be modified");
                        }

                    // remove "node" by linking the node's prev to the node's next
                    // note: this updates the head var if index==0
                    nodeVar.set(nextLink.get(node));

                    // unlink the old node (don't let it think it's still in this list)
                    nextLink.set(node, Null);

                    return True, this;
                    }
                }
            }
        else
            {
            Loop: for ( ; node != Null; node = nextLink.get(node))
                {
                if (node == value)
                    {
                    // remove "node" by linking the prev to the next and next to prev
                    Element? prev = prevLink.get(node);
                    Element? next = nextLink.get(node);
                    nextLink.set(prev?, next);           // prev?.next = next
                    prevLink.set(next?, prev);           // next?.prev = prev

                    // unlink the old node (don't let it think it's still in this list)
                    prevLink.set(node, Null);
                    nextLink.set(node, Null);

                    // update the head var
                    if (Loop.count == 0 && thisHeadVar)
                        {
                        set(next);
                        }

                    return True, this;
                    }
                }
            }

        return False;
        }

    @Override
    LinkedList insert(Int index, Element value)
        {
        cursor(index).insert(value);
        return this;
        }

    @Override
    LinkedList insertAll(Int index, Iterable<Element> values)
        {
        // TODO handle insert of a LinkedList<Element> (see addAll())

        Cursor cursor = cursor(index);
        for (Element e : values)
            {
            cursor.insert(e);
            cursor.advance();
            }
        return this;
        }

    @Override
    LinkedList delete(Int index)
        {
        cursor(index).delete();
        return this;
        }

    @Override
    LinkedList deleteAll(Interval<Int> interval)
        {
        Cursor cursor = cursor(interval.effectiveLowerBound);
        for (Int i = 0, Int c = interval.size; i < c; ++i)
            {
            assert cursor.exists;
            cursor.delete();
            }
        return this;
        }

    @Override
    LinkedList clear()
        {
        (Element? node, Link nextLink, Link? prevLink, Boolean thisHeadVar) = writeConfig();
        if (node == Null)
            {
            return this;
            }

        if (!thisHeadVar)
            {
            throw new ReadOnly("LinkedList head cannot be modified");
            }

        // unlink the nodes
        do
            {
            Element? next = nextLink.get(node);
            prevLink?.set(node, Null);
            nextLink.set(node, Null);
            node = next;
            }
        while (node != Null);

        // clear the head
        set(Null);

        return this;
        }

    @Override
    Cursor cursor(Int index = 0)
        {
        return new LinkedListCursor(index);
        }


    // ----- LinkedListCursor ----------------------------------------------------------------------

    /**
     * A LinkedListCursor is optimized for moving forwards (and possibly backwards) through a linked
     * list of nodes.
     *
     * Note that the the LinkedListCursor is neither stable nor fail-fast in the presence of
     * concurrent modification, since either would require state on the `LinkedList`.
     */
    protected class LinkedListCursor
            implements Cursor
        {
        /**
         * Construct a LinkedListCursor.
         *
         * @param index  the starting index to advance the cursor to
         */
        construct(Int index = 0)
            {
            (node, nextLink, prevLink, thisHeadVar, readOnly) = readConfig();
            this.head      = node;
            this.nodeIndex = 0;
            }
        finally
            {
            // the pointer to the first node is the head pointer
            nodeVar = headVar;

            // seek to the desired position
            this.index = index;
            }


        // ----- properties ------------------------------------------------------------------------

        /**
         * The current node.
         */
        protected Element? node;

        /**
         * The current index. This property holds the actual value for the [index] property.
         */
        protected Int nodeIndex;

        /**
         * The pointer to the current node, typically from the previous node, but for the head node,
         * this is either the pointer to the head node (it is "this" Var, iff `thisHeadVar` is
         * `True`), or it is `Null` iff `thisHeadVar` is `False`. It is necessary to maintain this
         * `nodeVar` because the cursor can go past the end of the LinkedList, and in that case,
         * this would be is the only reference that the cursor would have to the tail.
         */
        protected Var<Element?>? nodeVar;

        /**
         * The list head.
         */
        protected Element? head.get()
            {
            return headVar?.get() : super();
            }

        /**
         * The pointer to the list head, or `Null` if the parent of the LinkedList is actually the
         * list head (in which case, the head can neither be removed or replaced).
         */
        protected Var<Element?>? headVar.get()
            {
            return thisHeadVar ? this.LinkedList : Null;
            }

        /**
         * True iff the `LinkedList<Element>` (which is also a `Var<Element?>`) holds a reference to
         * the head element of the list, such that the head can be removed or replaced. (The
         * alternative is that the Var<Element?>, which is from a property, is part of a parent
         * object that itself is an Element, and is the implicit head of the list.)
         */
        protected Boolean thisHeadVar;

        /**
         * The element property that provides the next element.
         */
        protected Link nextLink;

        /**
         * The element property that provides the previous element.
         */
        protected Link? prevLink;

        /**
         * True iff the LinkedList does not allow modifications.
         */
        protected Boolean readOnly;


        // ----- Cursor operations -------------------------------------------------------------

        @Override
        @RO Boolean bidirectional.get()
            {
            return prevLink != Null;
            }

        @Override
        conditional Element next()
            {
            return advance()
                    ? (True, value)
                    : False;
            }

        @Override
        Int index
            {
            @Override
            Int get()
                {
                return nodeIndex;
                }

            @Override
            void set(Int newIndex)
                {
                Int oldIndex = nodeIndex;
                if (newIndex == oldIndex)
                    {
                    return;
                    }

                if (newIndex < oldIndex)
                    {
                    assert:bounds newIndex >= 0;
                    rewind(oldIndex - newIndex);
                    }
                else
                    {
                    advance(newIndex - oldIndex);
                    }
                }
            }

        @Override
        @RO Boolean exists.get()
            {
            return node != Null;
            }

        @Override
        Element value
            {
            @Override
            Element get()
                {
                return node ?: throw new OutOfBounds($"index={index}");
                }

            @Override
            void set(Element newNode)
                {
                // no-op if it's the same element already at this index
                Element? oldNode = this.node;
                if (&newNode == &oldNode)
                    {
                    return;
                    }

                // if the new value has previous or next pointers set already, then it's already in
                // a list and we can't use it (without destroying the other list)
                assert:arg nextLink.get(newNode) == Null && prevLink?.get(newNode) == Null;

                // ensure that the current position in the list can be modified
                ensureWritable();

                // link the new node into the list
                assert Var<Element?> nodeVar ?= this.nodeVar;
                nodeVar.set(newNode);               // note: this updates the head var if index==0
                Element? nextNode = nextLink.get(oldNode?) : Null;
                nextLink.set(newNode, nextNode?);   // link the newNode to the next

                // add the backwards links if the list is bidirectional
                if (Link prevLink ?= this.prevLink)
                    {
                    prevLink.set(newNode, prevLink.get(oldNode?)?);
                    prevLink.set(nextNode?, newNode);
                    }

                // store the newNode as the current cursor node
                node = newNode;

                // clear the links on the oldNode (don't let it think that it's still in this list)
                if (oldNode != Null)
                    {
                    nextLink.set(oldNode, Null);
                    prevLink?.set(oldNode, Null);
                    }
                }
            }

        @Override
        Boolean advance()
            {
            if (exists)
                {
                advance(1);
                return exists;
                }

            return False;
            }

        @Override
        void insert(Element newNode)
            {
            // if the new value has previous or next pointers set already, then it's already in
            // a list and we can't use it (without destroying the other list)
            assert:arg nextLink.get(newNode) == Null && prevLink?.get(newNode) == Null;

            ensureWritable();

            // link it into the list *before* the current node
            Element? oldNode = node;            // note: might be Null
            nodeVar?.set(newNode) : assert;     // note: this updates the head var if index==0
            Element? nextNode = nextLink.get(oldNode?) : Null;
            nextLink.set(newNode, nextNode);    // link the newNode to the next

            // add the backwards links if the list is bidirectional
            if (Link prevLink ?= this.prevLink)
                {
                prevLink.set(newNode, prevLink.get(oldNode?)?);
                prevLink.set(nextNode?, newNode);
                }

            // store the newNode as the current cursor node
            node = newNode;

            // clear the links on the oldNode (don't let it think that it's still in this list)
            if (oldNode != Null)
                {
                nextLink.set(oldNode, Null);
                prevLink?.set(oldNode, Null);
                }
            }

        @Override
        void delete()
            {
            // the cursor has to be on an element
            Element oldNode = node ?: assert:bounds;

            // the cursor has to be able to delete the current element
            ensureWritable();

            // unlink the current node from the list
            Element? nextNode = nextLink.get(oldNode);
            nodeVar?.set(nextNode) : assert;    // note: this updates the head var if index==0
            if (Link prevLink ?= this.prevLink)
                {
                prevLink.set(nextNode?, prevLink.get(oldNode));
                }

            // the next node is now the current node (because the old current node was deleted)
            node = nextNode;

            // clear the links on the oldNode (don't let it think that it's still in this list)
            nextLink.set(oldNode, Null);
            prevLink?.set(oldNode, Null);
            }


        // ----- internal operations -----------------------------------------------------------

        /**
         * Make sure that the cursor is writable (for changing the element value, inserting an
         * element, or deleting an element) at the current position.
         *
         * @throws ReadOnly  if the list has been configured as read-only, or if the current element
         *                   is not modifiable (which can occur if the element at the head of the
         *                   list is actually the object itself that contains the list)
         */
        protected void ensureWritable()
            {
            if (readOnly)
                {
                throw new ReadOnly();
                }

            if (nodeVar == Null)
                {
                assert index == 0 && headVar == Null;
                throw new ReadOnly("LinkedList head cannot be modified");
                }
            }

        /**
         * @param count  the number of nodes to advance, in the range `[1..size-index]`
         */
        protected void advance(Int count)
            {
            assert:arg count > 0;

            Element? cur  = node ?: assert;
            Element? prev = Null;
            for (Int i = 0; i < count; ++i)
                {
                prev = cur;
                cur  = nextLink.get(cur?) : assert:bounds;
                }

            nodeVar    = nextLink.of(prev?) : assert;
            node       = cur;
            nodeIndex += count;
            }

        /**
         * @param count  the number of nodes to rewind, in the range `[1..index]`
         */
        protected void rewind(Int count)
            {
            Int oldIndex = nodeIndex;
            Int newIndex = oldIndex - count;
            assert:arg count > 0 && newIndex >= 0;

            // check if going backwards would be the shorter path
            if (count < newIndex, Link prevLink ?= this.prevLink)
                {
                // make sure that the current position in actually in the list; the cursor is
                // allowed to be past the end of the list, which means the current node is Null
                Element curNode;
                if (curNode ?= node)
                    {
                    }
                else
                    {
                    // the cursor is actually *past* the tail, so first we need to rewind *to* the
                    // tail; there is a pointer (i.e. nodeVar) to where we are (humorously, it's
                    // currently pointing to `Null`)
                    assert Var<Element?> nodeVar ?= this.nodeVar;

                    // and since we are not at the head (we wouldn't be rewinding if we were), the
                    // nodeVar (the pointer past the tail) MUST be on an Element
                    assert (_, val container) := nodeVar.isProperty();

                    // the tail and the element before the tail both have to exist; we wouldn't be
                    // rewinding if they didn't (there's a minimum theoretical list size of 3 to
                    // even get in this rewind-by-backing-up logic at all)
                    Element tail = container.as(Element);
                    assert Element preTail ?= prevLink.get(tail);

                    // we successfully rewound to the tail; after this, it gets easy
                    curNode = tail;
                    --count;
                    }

                // fast rewind (since we don't have to worry about running past the end of the list,
                // there's no worries with bounds or null checks etc.)
                while (count-- > 0)
                    {
                    curNode = prevLink.get(curNode) ?: assert;
                    }

                // since the index is somewhere in the middle of the list, we know that there is a
                // previous node
                assert Element preNode ?= prevLink.get(curNode);

                // store the resulting cursor location
                node      = curNode;
                nodeVar   = nextLink.of(preNode);
                nodeIndex = newIndex;
                }
            else
                {
                // skip straight to the head
                node      = head;
                nodeVar   = headVar;
                nodeIndex = 0;

                // fast forward to the desired index
                if (newIndex > 0)
                    {
                    advance(newIndex);
                    }
                }
            }
        }
    }