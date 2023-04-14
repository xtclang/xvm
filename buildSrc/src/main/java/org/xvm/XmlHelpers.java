package org.xvm;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Skeleton class for common build logic, tasks and plugins
 * written in Java.
 *
 * We could declare any task with any name like:
 *
 * task("something") {
 *   doLast {
 *     BuilderHelpers.sayHello()
 *   }
 * }
 *
 * (Or register it, if it is to be attached to part of the predefined
 *  life cycle, ar run automatically somewhere in the build. The example
 *  above is just callable through .e.g "./gradlew something")
 */

public class XmlHelpers {
    public static void xmlVisitDomNode(final Node node) {
        xmlVisitDomNode(node, 0);
    }

    public static void xmlVisitDomNode(Node node, int level) {
        System.out.println("XmlVisitDomNode.Name:" + node.getNodeName());
        System.out.println("XmlVisitDomNode.Value:" + node.getNodeValue());
        final NodeList list = node.getChildNodes();
        for (int i = 0, length = list.getLength(); i < length; i++) {
            final Node childNode = list.item(i);
            xmlVisitDomNode(childNode, level + 1);
        }
    }
}
