package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Part;
import org.xvm.cc_explore.XEC;

/**
  Exploring XEC Constants
  A forwarding pointer to another component
 */
public abstract class IdCon extends Const {
  private transient Part _cache;
  public void resetCachedInfo() { _cache = null; }
  
  /**
   * @return the Component structure that is identified by this IdentityConstant
   */
  public Part getComponent() {
    if( _cache != null ) return _cache;
    throw XEC.TODO();
  }
  
}
