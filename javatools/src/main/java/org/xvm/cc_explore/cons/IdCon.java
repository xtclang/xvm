package org.xvm.cc_explore.cons;

import org.xvm.cc_explore.Component;
import org.xvm.cc_explore.XEC;
import java.io.IOException;
import java.util.Arrays;

/**
  Exploring XEC Constants
  A forwarding pointer to another component
 */
public abstract class IdCon extends Const {
  private transient Component _cache;
  public void resetCachedInfo() { _cache = null; }
  
  /**
   * @return the Component structure that is identified by this IdentityConstant
   */
  public Component getComponent() {
    if( _cache != null ) return _cache;
    throw XEC.TODO();
  }
  
}
