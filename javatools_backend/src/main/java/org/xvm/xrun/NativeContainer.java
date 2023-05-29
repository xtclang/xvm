package org.xvm.xrun;

public class NativeContainer extends Container {

  final NativeConsole _console;
  
  public NativeContainer() {
    super(null,null);
    _console = new NativeConsole();
  }

  @Override public NativeConsole console() { return _console; }
  
  // Initialize default things into the container?
  void init() {
  }

}
