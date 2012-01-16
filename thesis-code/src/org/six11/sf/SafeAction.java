package org.six11.sf;

public abstract class SafeAction {

  private String name;

  public SafeAction(String name) {
    this.name = name;
  }

  /**
   * Perform the action. This is invoked when the user does something like adds ink. It can also be
   * used to redo that action if it has been undone with the 'backward' function.
   */
  public abstract void forward();

  /**
   * Rolls back the action, if possible.
   */
  public abstract void backward();

  public final String getName() {
    return name;
  }

}
