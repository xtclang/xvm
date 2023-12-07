package org.xvm.xec.ecstasy;

import org.xvm.XEC;

public interface Object extends Comparable {

  default Object makeImmutable() {
    throw XEC.TODO();
  }
}
