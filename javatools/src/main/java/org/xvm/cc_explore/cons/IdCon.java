package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.XEC;

/**
  Exploring XEC Constants
  A forwarding pointer to another component
 */
public abstract class IdCon extends Const {
  abstract public String name();
  public IdCon link_as(XEC.ModRepo repo) { throw XEC.TODO(); }
}
