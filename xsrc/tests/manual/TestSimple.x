module TestSimple
    {
    class Tree<Element  extends Orderable>
        {
        Node? root = Null;

        void add(Element e)
            {
            if (root.is(Node))
                {
                // ToDo:
                }
            else
                {
                root = new Node(e);
                }
            }

        class Node
            {
            construct(Orderable data)
                {
                this.data  = data;
                this.links = new Array<Node?>();
                }

            public/private Orderable data;
            private Node?[] links;
            }
        }

    void run()
        {
        Tree<Int> tree = new Tree();
        tree.add(1);
        }
    }