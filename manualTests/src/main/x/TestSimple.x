module TestSimple {
    @Inject Console console;

    void run() {
    }

    interface Node {
        Node? prev;
        Node? next;
    }

    class ElementNode(Node? prev, Node? next)
            implements Node {

        ElementNode test(Node? node) {
            while (!node.is(ElementNode)) {
                prev = node;
                node = node?.next;
            }
            return node; // used to fail to compile
        }
    }
}