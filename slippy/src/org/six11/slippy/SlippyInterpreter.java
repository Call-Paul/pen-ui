package org.six11.slippy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.antlr.runtime.tree.Tree; // import org.six11.slippy.Thing.Function;
import org.six11.olive.DiskEnvironment;
import org.six11.util.Debug;
import org.six11.util.args.Arguments;
import org.six11.util.args.Arguments.ArgType;
import org.six11.util.args.Arguments.ValueType;
import org.six11.util.io.FileUtil; // import static org.six11.slippy.SlippyLexer.*;
import static org.six11.slippy.SlippyParser.*;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class SlippyInterpreter {

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    Arguments arguments = new Arguments();
    arguments.addFlag("tree", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "Shows the abstract syntax tree");
    arguments.addFlag("quit", ArgType.ARG_OPTIONAL, ValueType.VALUE_IGNORED,
        "Quit the interpreter after showing the AST");
    arguments.addFlag("load-path", ArgType.ARG_OPTIONAL, ValueType.VALUE_REQUIRED,
        "The load path to slippy code (defaults to path of main Slippy file)");
    arguments.addPositional(0, "file", ValueType.VALUE_REQUIRED, "The main Slippy source file");
    arguments.parseArguments(args);
    arguments.validate();
    SlippyInterpreter interp = new SlippyInterpreter();
    interp.useArguments(arguments);
  }

  private SlippyMachine machine;
  List<Thing> noArgs = new ArrayList<Thing>();
  private Tree lastEvalTree = null;
  private Stack<SlippyLocation> executionStack = new Stack<SlippyLocation>();

  public SlippyInterpreter() {
    machine = new SlippyMachine(this);
  }

  public void useArguments(Arguments args) throws Exception {
    String file = args.getValue("file");
    String cp = args.hasFlag("load-path") ? args.getValue("load-path") : FileUtil.getPath(file);
    Environment env = new DiskEnvironment(cp);
    machine.setEnvironment(env);
    String program = FileUtil.loadStringFromFile(file);
    if (args.hasFlag("tree")) {
      // show me the parse tree instead of interpreting
      Tree root = makeTree(program);
      SlippyDebugger.processTree(root, 1);
    }
    new Affine(this);
    machine.pushFileName(file);
    if (!args.hasFlag("quit")) {
      handleInput(program);
    }
  }

  public Tree makeTree(String programString) throws Exception {
    CharStream cs = new ANTLRStringStream(programString);
    SlippyLexer myLexer = new SlippyLexer(cs);
    CommonTokenStream tokens = new CommonTokenStream(myLexer);
    SlippyParser myParser = new SlippyParser(tokens);

    SlippyParser.prog_return r = myParser.prog();
    CommonTree root = ((CommonTree) r.tree);

    CommonTreeNodeStream nodes = new CommonTreeNodeStream(root);
    nodes.setTokenStream(tokens);
    return root;
  }

  public void runFile(String fileName) throws FileNotFoundException, IOException {
    String program = FileUtil.loadStringFromFile(fileName);
    machine.pushFileName(fileName);
    handleInput(program);
    machine.popFileName(true);
  }

  public void handleInput(String input) {
    try {
      interpret(input);
    } catch (Exception ex) {
      Debug.out("SlippyInterpreter", "Caught exception, consuming it and staying alive.");
      ex.printStackTrace(Debug.outputStream);
    }
  }

  public void interpret(final String programString) throws Exception {
    if (programString.length() == 0)
      return;

    Tree root = makeTree(programString);
    discover(root);
  }

  private void discover(Tree t) {
    switch (t.getType()) {
      case PROG:
      case BLOCK:
      case STMT:
        discoverAllChildren(t);
        break;
      case DEF_CLASS:
        stashClass(t);
        break;
      case DEF_FIELD:
        stashField(t);
        break;
      case DEF_FUNCTION:
        stashFunction(t);
        break;
      case EXPR:
        Thing ret = eval(t.getChild(0));
        machine.setReturnValue(ret);
        break;
      case EXPR_ASSIGN: // can happen in class discovery
        eval(t);
        break;
      case EXPR_UNARY_POS:
        initializeVariable(t.getChild(0), false); // can happen in class discovery
        break;
      case STMT_CODESET_DECL:
        ret = doCodesetExpr(t);
        break;
      case STMT_IMPORT:
        importAll(t);
        break;
      case STMT_WHILE:
        doWhile(t);
        break;
      case STMT_IF:
        doIf(t);
        break;
      case STMT_LOOP:
        doLoop(t);
        break;
      default:
        err("Unknown tree node in discover(): " + SlippyDebugger.bug(t, true));
    }
  }

  private static void bug(String what) {
    Debug.out("SlippyInterpreter", what);
  }

  private void bug(String where, Tree t) {
    bug("Showing tree for: " + where);
    SlippyDebugger.processTree(t, 0);
  }

  private String bugLocationShort(Tree t) {
    String f = machine.getCurrentFile();
    if (f.lastIndexOf('/') > 0 && (f.lastIndexOf('/') + 1) < f.length()) {
      f = f.substring(f.lastIndexOf('/') + 1);
    }
    return f + ":" + t.getLine() + ":" + t.getCharPositionInLine();
  }

  private String bugLocation(Tree t) {
    return machine.getCurrentFile() + ":" + t.getLine() + ":" + t.getCharPositionInLine();
  }

  private void err(String what) {
    Debug.out("> SlippyInterpreter", what);
  }

  /**
   * This handles a file's import statements.
   */
  private void importAll(Tree t) {
    for (int i = 0; i < t.getChildCount(); i++) {
      importFromTree(t.getChild(i));
    }
  }

  /**
   * Imports a single fully-qualified class based on the contents of the tree.
   */
  private void importFromTree(Tree t) {
    StringBuilder pathBuf = new StringBuilder();
    StringBuilder codesetBuf = new StringBuilder();

    // the first child is the dot-separated codeset, up to but not including the classname
    Tree codeSet = t.getChild(0);
    for (int i = 0; i < codeSet.getChildCount(); i++) {
      pathBuf.append(File.separator + codeSet.getChild(i).getText());
      if (i > 0) {
        codesetBuf.append(".");
      }
      codesetBuf.append(codeSet.getChild(i).getText());
    }

    // second child is simply the classname
    String baseName = t.getChild(1).getText();

    importFromFile(baseName, codesetBuf.toString());
  }

  public Thing importFromFile(String className, String codesetStr) {
    Thing ret = Thing.NIL;

    if (!machine.getCodeset(codesetStr).hasClass(className)) {
      String fullFileName = machine.getEnvironment().getFullFileName(className, codesetStr);
      if (!machine.isFileLoaded(fullFileName)) {
        try {
          machine.pushFileName(fullFileName);
          String program = machine.getEnvironment().loadStringFromFile(fullFileName); // machine.loadStringFromFile(fullFileName);
          handleInput(program);
        } catch (Exception ex) {
          String callingFile = machine.getCallingFile();
          err("Can't load source file '" + fullFileName + "'"
              + (callingFile == null ? "" : ", referenced from " + callingFile));
        } finally {
          machine.popFileName(true);
        }
      }
    }
    Thing.Clazz c = machine.findClass(codesetStr, className);
    if (c != null) {
      try {
        machine.addImport(machine.getCurrentFile(), c);
      } catch (EmptyStackException ignore) {
      }
      ret = c;
    }
    return ret;
  }

  private Thing doCodesetExpr(Tree t) {
    Thing ret = Thing.NIL;
    if (t.getType() == STMT_CODESET_DECL || t.getType() == STMT_CODESET) {
      StringBuilder codesetStr = new StringBuilder();
      StringBuilder pathStr = new StringBuilder();
      for (int i = 0; i < t.getChildCount(); i++) {
        if (i > 0) {
          codesetStr.append(".");
          pathStr.append(File.separator);
        }
        codesetStr.append(t.getChild(i).getText());
        pathStr.append(t.getChild(i).getText());
      }
      ret = machine.getCodeset(codesetStr.toString());// new Thing.Codeset(codesetStr.toString(),
      if (t.getType() == STMT_CODESET_DECL) {
        // this refers to the codeset for the current file.
        machine.setCodeset((Thing.Codeset) ret); // maps current file with codeset
      }
    }
    return ret;

  }

  private void stashClass(Tree t) {
    Thing.Clazz c = new Thing.Clazz(t.getChild(0).getText(), machine.getCodeset(), machine
        .getCurrentFile());
    machine.getCodeset().addClass(c);
    machine.addImport(machine.getCurrentFile(), c);
    machine.pushSymbolTable(new SymbolTable());
    for (int i = 1; i < t.getChildCount(); i++) {
      Tree part = t.getChild(i);
      if (part.getType() == EXPR_CLASS_EXTENDS) {
        String otherClassName = eval(part.getChild(0).getChild(0)).toString()
            + part.getChild(0).getChild(1).toString();
        c.superClassName = otherClassName;
      } else if (part.getType() == EXPR_CLASS_MIXES) {
        for (int j = 0; j < part.getChildCount(); j++) {
          String mixer = eval(part.getChild(j)).toString();// part.getChild(j).getText();
          c.mixedClassNames.add(mixer);
        }
      } else {
        discover(part);
      }
    }
    c.symbols = SlippyUtils.shallowCopy(machine.getSymbolTable());
    c.symbols.setName("Symbol table for class " + c.name);
    c.symbols.clazz = c;
    machine.popSymbolTable();
    // TODO: might want to help the user out here by ensuring that the class is in the right file:
    // if (!SlippyUtils.isCorrectCodeset(c, machine.getCurrentFile(), machine.getLoadPath())) {
    // complain();
    // }
  }

  private Thing.Function stashFunction(Tree t) {
    String symbolName = t.getChild(0).getText();
    Thing.Function lambda = doLambdaDecl(t.getChild(1), t.getChild(2));
    lambda.name = symbolName;
    for (int i = 3; i < t.getChildCount(); i++) {
      if (t.getChild(i).getType() == ANNOTATION) {
        recordAnnotation(symbolName, t.getChild(i));
      }
    }
    machine.set(symbolName, lambda);
    return lambda;
  }

  private Thing.Function doLambdaDecl(Tree paramTree, Tree blockTree) {
    List<String> paramNames = new ArrayList<String>();
    for (int i = 0; i < paramTree.getChildCount(); i++) {
      paramNames.add(paramTree.getChild(i).getText());
    }
    Thing.Function f = new Thing.Function("lambda", paramNames, machine.getCurrentFile());
    f.block = blockTree;
    return f;
  }

  private void stashField(Tree t) {
    String symbolName = t.getChild(0).getText();
    Thing.Variable rval = new Thing.Variable(symbolName);

    // record the field's annotations.
    for (int i = 1; i < t.getChildCount(); i++) {
      Tree child = t.getChild(i);
      if (child.getType() == ANNOTATION) {
        recordAnnotation(symbolName, child);
      } else {
        rval.value = SlippyUtils.dereference(eval(t.getChild(1)));
      }
    }
    machine.set(symbolName, rval);
  }

  public Thing eval(Tree t) {
    this.lastEvalTree = t; // Save this location for stacktraces
    Thing ret = Thing.NIL;
    try {
      switch (t.getType()) {
        case EXPR_ADD:
          ret = doArithmeticExpr(t.getChild(0), t.getChild(1), '+');
          break;
        case EXPR_AND:
          ret = doAndOr("and", t.getChild(0), t.getChild(1));
          break;
        case EXPR_ASSIGN:
          ret = doAssignmentExpr(t);
          break;
        case EXPR_ARRAY_INDEX:
          ret = doIndexedArrayExpr(t.getChild(0), t.getChild(1));
          break;
        case EXPR_ARRAY_INIT:
          ret = doArrayInitExpr(t);
          break;
        case STMT_CODESET:
          ret = doCodesetExpr(t);
          break;
        case EXPR_CONSTRUCTOR:
          ret = doConstructorExpr(t);
          break;
        case EXPR_DIV:
          ret = doArithmeticExpr(t.getChild(0), t.getChild(1), '/');
          break;
        case EXPR_EQ:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), '=');
          break;
        case EXPR_FQ_CLASS_NAME:
          ret = getFullyQualifiedClassName(t);
          break;
        case EXPR_GT:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), '>');
          break;
        case EXPR_GTEQ:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), ']');
          break;
        case EXPR_LAMBDA:
          ret = doLambdaDecl(t.getChild(0), t.getChild(1));
          break;
        case EXPR_LT:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), '<');
          break;
        case EXPR_LTEQ:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), '[');
          break;
        case EXPR_MAP_INIT:
          ret = doMapInitExpr(t);
          break;
        case EXPR_MEMBER:
          ret = evalMemberExpr(t);
          break;
        case EXPR_MINUS:
          ret = doArithmeticExpr(t.getChild(0), t.getChild(1), '-');
          break;
        case EXPR_MODULO:
          ret = doArithmeticExpr(t.getChild(0), t.getChild(1), '%');
          break;
        case EXPR_MULT:
          ret = doArithmeticExpr(t.getChild(0), t.getChild(1), '*');
          break;
        case EXPR_NEQ:
          ret = doEqualityExpr(t.getChild(0), t.getChild(1), '!');
          break;
        case EXPR_UNARY_NEG:
          ret = new Thing.Num("-" + eval(t.getChild(0)));
          break;
        case EXPR_OR:
          ret = doAndOr("or", t.getChild(0), t.getChild(1));
          break;
        case EXPR_UNARY_NOT:
          ret = new Thing.Bool(!SlippyUtils.isThingTrue(eval(t.getChild(0))));
          break;
        case EXPR_UNARY_POS:
          ret = eval(t.getChild(0));
          break;
        case FALSE:
          ret = new Thing.Bool(false);
          break;
        case EXPR_FUNC_CALL:
          ret = doFunctionCallExpr(t);
          break;
        case ID:
          ret = doIdentifierEval(t);
          break;
        case NUM:
          ret = new Thing.Num(t.getText());
          break;
        case STR_LITERAL:
          ret = new Thing.Str(SlippyUtils.stripQuotes(t.getText()));
          break;
        case TRUE:
          ret = new Thing.Bool(true);
          break;
        default:
          err(bugLocation(t) + ": Unable to eval tree: " + SlippyDebugger.bug(t, true));
          err(SlippyDebugger.bug(t, true));
      }
    } catch (Exception ex) {
      err(bugLocationShort(t) + ": " + ex.getMessage());
      ex.printStackTrace(SlippyMachine.outputStream);
    }
    return ret;
  }

  private Thing doAndOr(String string, Tree child, Tree child2) {
    Thing ret = Thing.NIL;
    Thing left = eval(child);
    boolean leftVal = SlippyUtils.isThingTrue(left);
    if (string.equals("and")) {
      if (leftVal == false) {
        ret = new Thing.Bool(false);
      } else {
        Thing right = eval(child2);
        boolean rightVal = SlippyUtils.isThingTrue(right);
        ret = new Thing.Bool(rightVal && leftVal);
      }
    } else if (string.equals("or")) {
      if (!leftVal) { // left val is true, no need to perform right side.
        Thing right = eval(child2);
        boolean rightVal = SlippyUtils.isThingTrue(right);
        ret = new Thing.Bool(rightVal || leftVal);
      } else {
        ret = new Thing.Bool(true);
      }
    } else {
      err(bugLocation(child) + ": invalid boolean operator: " + string);
    }
    return ret;
  }

  private Thing getFullyQualifiedClassName(Tree t) {
    Thing ret = Thing.NIL;
    Thing cs = eval(t.getChild(0));
    String name = t.getChild(1).getText();
    if (cs.toString().length() > 0) {
      ret = new Thing.Str(cs.toString() + "." + name);
    } else {
      ret = new Thing.Str(name);
    }
    return ret;
  }

  private Thing initializeVariable(Tree t, boolean shadowAllowed) {
    Thing ret = Thing.NIL;
    if (t.getType() == ID) {
      Thing value = eval(t);
      String name = t.getChild(0).getText();
      if (value == Thing.NIL && !shadowAllowed) { // do not
        ret = machine.initializeVariable(name);
      } else {
        // TODO: shadowing not allowed. warn user?
      }
    } else {
      // TODO: can't create a variable with this name. examine tree for more info.
      err("Can't create a variable for this tree:");
      SlippyDebugger.processTree(t, 0);
    }
    return ret;
  }

  private ResolveData searchTable(String name, SymbolTable table) {
    ResolveData ret = new ResolveData();
    SymbolTable cursor = table;
    while (cursor != null) {
      if (cursor.symbols.get(name) != null) {
        ret = new ResolveData(cursor.symbols.get(name), table);
        break;
      }
      cursor = cursor.parent;
    }
    return ret;
  }

  private ResolveData resolve(Tree t, boolean insertMissing, boolean checkGlobal,
      boolean complainOnError) {
    ResolveData ret = new ResolveData();
    String firstError = null;
    ret = resolveWithTable(t, insertMissing, machine.getSymbolTable());
    firstError = ret.error;
    if (!ret.valid && checkGlobal && machine.getSymbolTable() != machine.getGlobalTable()) {
      ret = resolveWithTable(t, insertMissing, machine.getGlobalTable());
    }
    if (complainOnError && firstError != null && ret.error != null) {
      err(ret.error);
    }
    return ret;
  }

  private ResolveData resolveWithTable(Tree t, boolean insertMissing, SymbolTable table) {
    ResolveData ret = new ResolveData();
    ResolveData left, right;
    switch (t.getType()) {
      case EXPR_MEMBER:
        left = resolveWithTable(t.getChild(0), false, table); // insertMissing = false on purpose.
        if (!left.valid) {
          // Perhaps it is a class?
          if (t.getChild(0).getType() == ID) {
            try {
              Thing.Clazz maybeClass = machine.findClass(t.getChild(0).getText());
              left = new ResolveData(maybeClass, SlippyUtils.getSymbolTable(maybeClass, null));
            } catch (RuntimeException ignore) {
            }
          }
        }
        if (left.valid) {
          SymbolTable lvalTable = SlippyUtils.getSymbolTable(left.getDeref(), null);
          if (lvalTable == null) {
            ret.error = bugLocation(t.getChild(0)) + ": no members for type "
                + left.getDeref().type;
          } else {
            right = resolveWithTable(t.getChild(1), insertMissing, lvalTable);
            ret = right;
          }
        } else {
          ret.error = bugLocationShort(t.getChild(0)) + ": Null reference";
        }
        break;
      case ID:
        String name = t.getText();
        ret = searchTable(name, table);
        if (!ret.valid && insertMissing) {
          table.setThing(name, new Thing.Variable(name));
          ret = new ResolveData(table.getThing(name), table);
        }
        break;
      case EXPR_ARRAY_INDEX:
        left = resolveWithTable(t.getChild(0), false, table);
        if (left.valid) {
          if (left.getDeref().type == Thing.Type.Array) {
            int idx = SlippyUtils.toInt(eval(t.getChild(1)).toString());
            Thing.Array arr = (Thing.Array) left.getDeref();
            arr.accommodate(idx);
            Thing rVal = arr.getSlot(idx);
            ret = new ResolveData(rVal, SlippyUtils.getSymbolTable(rVal, machine.getSymbolTable()));
          } else if (left.getDeref().type == Thing.Type.Map) {
            Thing idx = eval(t.getChild(1));
            Thing.Map m = (Thing.Map) left.getDeref();
            Thing rVal = m.getSlot(idx);
            ret = new ResolveData(rVal, SlippyUtils.getSymbolTable(rVal, machine.getSymbolTable()));
          }
        } else {
          left.error = bugLocation(t.getChild(0)) + ": symbol not found";
        }
        break;
      case EXPR_FUNC_CALL:
        left = resolveWithTable(t.getChild(0), false, table);
        if (left.getDeref().type == Thing.Type.Function) {
          SymbolTable funcContext = machine.getSymbolTable();
          if (left.valid && left.table.isInstance()) {
            funcContext = left.table.getInstance().symbols;
          } else if (left.valid && left.table.isSlippyClass()) {
            funcContext = left.table.getSlippyClass().symbols;
          }
          Thing response = invokeFunction((Thing.Function) left.getDeref(), t.getChild(1),
              funcContext);
          ret = new ResolveData(response, SlippyUtils.getSymbolTable(response, machine
              .getSymbolTable()));
        } else {
          ret.error = bugLocationShort(t) + ": not a function";
        }
        break;
      default:
    }
    return ret;
  }

  private Thing doConstructorExpr(Tree t) {
    Thing ret = Thing.NIL;
    String className = t.getChild(0).getText();
    Thing maybeClazz = machine.findClass(className);
    if (maybeClazz != null && maybeClazz.type == Thing.Type.Class) {
      Thing.Clazz c = (Thing.Clazz) maybeClazz;
      Thing.Instance retAsInst = new Thing.Instance(c);
      Tree params = t.getChild(1);
      maybeDoFunction(retAsInst.symbols.getThing("init"), c, retAsInst, params);
      for (Thing.Clazz m : c.mixedClasses) {
        maybeDoFunction(m.symbols.getThing("mix"), m, retAsInst, null);
      }
      ret = retAsInst;
    }
    return ret;
  }

  /**
   * This is apparently only called for constructors.
   */
  private void maybeDoFunction(Thing targetFunction, Thing.Clazz declaringClass,
      Thing.Instance inst, Tree params) {
    if (targetFunction.type != Thing.Type.Function) {
      return;
    }

    machine.pushFileName(declaringClass.sourceFileName);
    boolean error = false;
    try {
      invokeFunction((Thing.Function) targetFunction, params, inst.symbols);
    } catch (Exception ex) {
      ex.printStackTrace();
      error = true;
    } finally {
      machine.popFileName(false);
      if (error) {
        err("Tiny black hole created during instance method invokation at "
            + bugLocation(((Thing.Function) targetFunction).block));
      }
    }

  }

  private Thing doAssignmentExpr(Tree t) {
    Thing ret = Thing.NIL;
    Thing rVal = eval(t.getChild(1));
    Tree left = t.getChild(0);
    ResolveData res = resolve(left, true, true, true);
    Thing lVal = res.getThing(); // need the variable itself, so don't use deref()
    if (lVal.type == Thing.Type.Variable) {
      ((Thing.Variable) lVal).value = rVal;
      ret = rVal;
    } else {
      bug("Tree associated with forthcoming error: ", t);
      err(bugLocation(t) + ": Can't assign into item of type " + lVal.type);
    }
    return ret;
  }

  private Thing doArrayInitExpr(Tree t) {
    Thing.Array ret = new Thing.Array(this);
    for (int i = 0; i < t.getChildCount(); i++) {
      ret.addValue(eval(t.getChild(i)));
    }
    return ret;
  }

  private Thing doMapInitExpr(Tree t) {
    Thing.Map ret = new Thing.Map();
    for (int i = 0; i < t.getChildCount(); i++) {
      Thing left = eval(t.getChild(i).getChild(0));
      Thing right = eval(t.getChild(i).getChild(1));
      ret.put(left, right);
    }
    return ret;
  }

  private Thing doArithmeticExpr(Tree a, Tree b, char op) {
    Thing ret = Thing.NIL;
    Thing ra = eval(a);
    Thing rb = eval(b);
    if (ra.type == rb.type && ra.type == Thing.Type.Number) {
      double aVal = ((Thing.Num) ra).v;
      double bVal = ((Thing.Num) rb).v;
      double result = 0.0;
      switch (op) {
        case '+':
          result = aVal + bVal;
          break;
        case '-':
          result = aVal - bVal;
          break;
        case '*':
          result = aVal * bVal;
          break;
        case '/':
          result = aVal / bVal;
          break;
        case '%':
          result = ((int) aVal) % ((int) bVal);
          break;
        default:
          Debug.out("SlippyInterpreter", "Unknown arithmetic operator: " + op);
      }
      ret = new Thing.Num(result);
      // TODO: see if there is a user-defined operator for these two things.
      // } else if (operatorExists(ra, rb, op)) {
    } else if (op == '+') {
      // NOTE: This should be the last else..if clause as a default
      String raS = ra.toString();
      String rbS = rb.toString();
      if (ra instanceof Thing.Instance) {
        Thing result = invokeInstanceFunction((Thing.Instance) ra, "to_s", noArgs);
        if (result != Thing.NIL) {
          raS = result.toString();
        }
      }
      if (rb instanceof Thing.Instance) {
        Thing result = invokeInstanceFunction((Thing.Instance) rb, "to_s", noArgs);
        if (result != Thing.NIL) {
          rbS = result.toString();
        }
      }
      ret = new Thing.Str(raS + rbS);
    } else {
      err(bugLocation(a) + ": Can't apply operation " + op + " to types " + ra.type + " and "
          + rb.type);
    }
    return ret;
  }

  Thing invokeInstanceFunction(Thing.Instance inst, String funcName, List<Thing> args) {
    Thing ret = Thing.NIL;
    if (inst.symbols.hasThing(funcName)) {
      ret = invokeFunction((Thing.Function) inst.symbols.getThing(funcName), args, inst.symbols);
    }
    return ret;
  }

  private Thing doFunctionCallExpr(Tree t) {
    Thing ret = Thing.NIL;
    ResolveData res = resolve(t.getChild(0), false, true, true);
    if (res.valid && res.getDeref().type == Thing.Type.Function) {
      Thing.Function function = (Thing.Function) res.getDeref();
      Tree params = t.getChild(1);
      List<Thing> argVals = new ArrayList<Thing>();
      if (params != null) {
        for (int i = 0; i < params.getChildCount(); i++) {
          Thing thing = SlippyUtils.dereference(eval(params.getChild(i)).copy());
          argVals.add(thing);
        }
      }
      SymbolTable funcContext = null;
      if (res.table.isInstance()) {
        funcContext = res.table.getInstance().symbols;
      } else if (res.table.isSlippyClass()) {
        funcContext = res.table.getSlippyClass().symbols;
      }
      ret = invokeFunction(function, argVals, funcContext);
    } else {
      if (res.error != null) {
        err("Woo I got an error from resolve! " + res.error);
      }
      // err(bugLocation(t) + ": Could not resolve function " + t.getChild(0));
      // Debug.dumpStack("SlippyInterpreter", bugLocation(t) + " Could not resolve function");
    }
    return ret;
  }

  /**
   * This defers to another invokeFunction method.
   */
  private Thing invokeFunction(Thing.Function function, Tree params, SymbolTable context) {
    List<Thing> argVals = new ArrayList<Thing>();
    if (params != null) {
      for (int i = 0; i < params.getChildCount(); i++) {
        Thing thing = SlippyUtils.dereference(eval(params.getChild(i)).copy());
        argVals.add(thing);
      }
    }

    return invokeFunction(function, argVals, context);
  }

  public Thing invokeFunction(Thing.Function function, List<Thing> argVals, SymbolTable context) {
    // String bugger = (context == null ? "No context!" : context.getDebugFull());
    // Debug.dumpStack("SlippyInterpreter", "calling function " + function + " using context:\n"
    // + bugger);
    Thing ret = Thing.NIL;
    pushStacktraceLocation();
    machine.pushFunctionName(function.name);
    if (function.isBuiltin()) {
      if (context != null) {
        SymbolTable funcTable = machine.pushSymbolTable(context);
        funcTable.setName("Function '" + function.name + "'");
      } else {
        SymbolTable funcTable = machine.pushSymbolTable(null);
        funcTable.setName("Function '" + function.name + "'");
      }
      try {
        ret = function.eval(argVals, context);
      } finally {
        machine.popSymbolTable();
      }
    } else if (machine.hasCachedVersion(function, argVals, context)) {
      ret = machine.getCachedVersion(function, argVals, context);
    } else {
      if (function.paramNames.size() == argVals.size()) {
        if (context != null) {
          SymbolTable funcTable = machine.pushSymbolTable(context);
          funcTable.setName("Function '" + function.name + "'");
        } else {
          SymbolTable funcTable = machine.pushSymbolTable(null);
          funcTable.setName("Function '" + function.name + "'");
        }
        for (int i = 0; i < function.paramNames.size(); i++) {
          String name = function.paramNames.get(i);
          Thing value = new Thing.Variable(argVals.get(i));
          machine.set(name, value);
        }
        try {
          machine.pushFileName(function.sourceFileName);
          discover(function.block);
        } finally {
          machine.popFileName(false);
        }
        ret = machine.getReturnValue();
        machine.popSymbolTable();
        machine.setCachedVersion(function, argVals, context, ret);
      } else {
        err("Error: wrong number of params for function: " + function + " (you provided "
            + Debug.num(argVals, ", ") + ").");
      }
    }
    machine.popFunctionName();
    popStacktraceLocation();
    return ret;
  }

  private SlippyLocation popStacktraceLocation() {
    return executionStack.pop();
  }

  private void pushStacktraceLocation() {
    executionStack.push(makeStacktraceLocation());
  }

  public SlippyLocation getStacktraceLocation() {
    return executionStack.peek();
  }

  public String getStacktrace() {
    StringBuilder buf = new StringBuilder();
    for (int i = executionStack.size() - 1; i >= 0; i--) {
      SlippyLocation loc = executionStack.get(i);
      buf.append("  " + loc);
      if (i > 0) {
        buf.append("\n");
      }
    }
    return buf.toString();
  }

  private SlippyLocation makeStacktraceLocation() {
    return new SlippyLocation(machine.getCurrentFile(), machine.getCurrentFunction(), lastEvalTree
        .getLine(), lastEvalTree.getCharPositionInLine());
  }

  private Thing doIdentifierEval(Tree t) {
    Thing ret = Thing.NIL;
    String name = t.getText();
    ResolveData res = resolve(t, false, true, true); // machine.find(name);
    ret = res.getDeref();
    if (ret == Thing.NIL) {
      try {
        if (Character.isUpperCase(name.charAt(0))) {
          Thing maybeClass = machine.findClass(name);
          ret = maybeClass;
        }
      } catch (Exception ignore) {
        // No big deal. It just means the identifier is unknown. That could be part of the
        // hacker's plan, for example: if(someUndeclaredVariable)
      }
    }
    return ret;
  }

  private Thing evalMemberExpr(Tree t) {
    Thing ret = Thing.NIL;
    ResolveData res = resolve(t, false, true, true); // TODO: maybe change complain to false?
    if (res.valid) {
      ret = res.getDeref();
    }
    return ret;

  }

  private void discoverAllChildren(Tree t) {
    for (int i = 0; i < t.getChildCount(); i++) {
      discover(t.getChild(i));
    }
  }

  public Thing doEqualityExpr(Tree at, Tree bt, char op) {
    Thing regA = eval(at);
    Thing regB = eval(bt);
    return equate(regA, regB, op);
  }

  public Thing equate(Thing regA, Thing regB, char op) {
    boolean result = false;
    if (regA.type == regB.type && regA.type == Thing.Type.Number) {
      float a = SlippyUtils.toFloat(regA.toString());
      float b = SlippyUtils.toFloat(regB.toString());
      switch (op) {
        case '<':
          result = a < b;
          break;
        case '[':
          result = a <= b;
          break;
        case '>':
          result = a > b;
          break;
        case ']':
          result = a >= b;
          break;
        case '=':
          result = a == b;
          break;
        case '!':
          result = a != b;
          break;
      }
    } else if (regA.type == regB.type && regA.type == Thing.Type.String) {
      String a = ((Thing.Str) regA).v;
      String b = ((Thing.Str) regB).v;
      switch (op) {
        case '=':
          result = a.equals(b);
          break;
        case '!':
          result = !a.equals(b);
          break;
      }
    } else if (regA.type == regB.type && regA.type == Thing.Type.Boolean) {
      boolean a = ((Thing.Bool) regA).v;
      boolean b = ((Thing.Bool) regB).v;
      switch (op) {
        case '=':
          result = a == b;
          break;
        case '!':
          result = a != b;
          break;
      }
    } else if (regA.type == Thing.Type.Nil ^ regB.type == Thing.Type.Class) {
      switch (op) {
        case '=':
          result = regA == regB;
          break;
        case '!':
          result = regA != regB;
          break;
      }
    } else if (regA.type == Thing.Type.Nil ^ regB.type == Thing.Type.Nil) {
      switch (op) {
        case '=':
          result = false;
          break;
        case '!':
          result = true;
          break;
      }
    } else if (regA.type == regB.type && regA.type == Thing.Type.Instance) {
      result = equateInstances((Thing.Instance) regA, (Thing.Instance) regB, op);
    } else if (regA.type == Thing.Type.Nil && regB.type == Thing.Type.Nil) {
      switch (op) {
        case '=':
          result = true;
          break;
        case '!':
          result = false;
          break;
      }
    } else {
      // throw new RuntimeException("Can't equate types " + regA.type + " and " + regB.type);
      // Debug.out("> SlippyInterpreter", "Can not equate types " + regA.type + " and " + regB.type
      // + " at " + at.getLine() + ":" + at.getCharPositionInLine());
    }

    return new Thing.Bool(result);
  }

  private boolean equateInstances(Thing.Instance a, Thing.Instance b, char op) {
    // invoke 'a.equals(b)'
    List<Thing> otherList = SlippyUtils.toThings(b);
    Thing result = invokeInstanceFunction((Thing.Instance) a, "equals", otherList);
    boolean ret;
    if (op == '=') {
      ret = SlippyUtils.isThingTrue(result);
    } else {
      ret = !SlippyUtils.isThingTrue(result);
    }
    return ret;
  }

  private Thing doIndexedArrayExpr(Tree symT, Tree idxT) {
    Thing ret = Thing.NIL;
    Thing sym = eval(symT);
    if (sym.type == Thing.Type.Array || sym.type == Thing.Type.Map) {
      ret = doIndexedArrayAccess(sym, idxT);
    } else {
      err(bugLocation(symT) + ": "
          + " not indexable. Sorry! Only arrays and maps can be used like that.");
    }
    return ret;
  }

  private Thing doIndexedArrayAccess(Thing sym, Tree idxT) {
    Thing ret = Thing.NIL;
    if (sym.type == Thing.Type.Array) {
      int idx = SlippyUtils.toInt(eval(idxT).toString());
      Thing.Array arr = (Thing.Array) sym;
      if (arr.size() >= idx) {
        ret = arr.getValue(idx);
      } else {
        // TODO: handle array out-of-bounds error
      }
    } else if (sym.type == Thing.Type.Map) {
      Thing.Map m = (Thing.Map) sym;
      Thing idx = eval(idxT);
      if (m.data.containsKey(idx)) {
        ret = m.data.get(idx);
      }
    } else {
      // TODO: handle type mismatch error
      throw new RuntimeException("doIndexedArrayAccess received input of type" + sym.type);
    }
    return ret;
  }

  public void doWhile(Tree t) {
    while (SlippyUtils.toTruth(eval(t.getChild(0)).toString())) {
      if (t.getChildCount() > 1) {
        discoverAllChildren(t.getChild(1));
      }
    }
  }

  private void doIf(Tree t) {
    Tree child;
    boolean didSomething = false;
    for (int i = 0; i < t.getChildCount(); i++) {
      child = t.getChild(i);
      if (child.getType() == STMT_CONDITION_BLOCK) {
        if (SlippyUtils.isThingTrue(eval(child.getChild(0)))) {
          if (child.getChildCount() > 1) {
            discover(child.getChild(1));
            didSomething = true;
          }
          break;
        }
      } else if (child.getType() == BLOCK && !didSomething) {
        // child is the else clause. It is not
        discover(child);
      }
    }
  }

  private void doLoop(Tree t) {
    if (t.getChildCount() == 3) {
      // this is loop( x : thing ) block-of-code done
      String varName = t.getChild(0).getText();
      int numIterations = 0;
      while (true) {
        Thing numOrList = eval(t.getChild(1));
        if (numOrList.type == Thing.Type.Array && numIterations >= ((Thing.Array) numOrList).size()) {
          break;
        } else if (numOrList.type == Thing.Type.Number
            && numIterations >= SlippyUtils.toInt(numOrList.toString())) {
          break;
        } else if (!(numOrList.type == Thing.Type.Number || numOrList.type == Thing.Type.Array)) {
          err("Invalid loop expression at " + bugLocation(t.getChild(1)) + ": found " + numOrList
              + " (type " + numOrList.type + ")");
          break;
        }
        machine.pushSymbolTable();
        if (numOrList.type == Thing.Type.Array) {
          Thing value = ((Thing.Array) numOrList).getValue(numIterations);
          machine.set(varName, value);
        } else {
          Thing value = new Thing.Num(numIterations);
          machine.set(varName, value);
        }
        discover(t.getChild(2));
        machine.popSymbolTable();
        numIterations++;
      }

    } else {
      // this is loop (expr) block-of-code done
      // expr could be anything: a number, a boolean, something else...
      Tree conditionalBlock = t.getChild(0);
      Tree conditionTree = conditionalBlock.getChild(0);
      Tree block = conditionalBlock.getChild(1);

      machine.pushSymbolTable();
      int numIterations = 0;
      while (true) {
        Thing condition = eval(conditionTree);
        if (condition.type == Thing.Type.Number
            && SlippyUtils.toInt(condition.toString()) <= numIterations) {
          break;
        } else if (condition.type == Thing.Type.Boolean
            && !SlippyUtils.toTruth(condition.toString())) {
          break;
        } else if (!(condition.type == Thing.Type.Number || condition.type == Thing.Type.Boolean)) {
          err("Invalid loop expression at " + bugLocation(conditionTree) + ": found " + condition
              + " (type " + condition.type + ")");
          break;
        }
        discover(block);
        numIterations++;
      }
      machine.popSymbolTable();
    }
  }

  public SlippyMachine getMachine() {
    return machine;
  }

  public boolean isValidClassName(String id) {
    CharStream cs = new ANTLRStringStream(id);
    SlippyLexer myLexer = new SlippyLexer(cs);
    CommonTokenStream tokens = new CommonTokenStream(myLexer);
    SlippyParser myParser = new SlippyParser(tokens);
    boolean ret = false;
    try {
      myParser.classID(); // will toss exception if 'id' is bad.
      ret = true;
    } catch (RecognitionException ex) {
      ex.printStackTrace();
    }
    return ret;
  }

  public void recordAnnotation(String symbolName, Tree annoteTree) {
    String annName = annoteTree.getChild(0).getText();
    Thing.Annotation annotation = new Thing.Annotation(annName);
    if (annoteTree.getChildCount() == 2) {
      Tree expressions = annoteTree.getChild(1);
      for (int j = 0; j < expressions.getChildCount(); j++) {
        annotation.addExpression(expressions.getChild(j));
      }
    }
    machine.addAnnotation(symbolName, annotation);
  }
}
