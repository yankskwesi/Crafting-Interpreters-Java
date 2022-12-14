package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lox.TokenType.SLASH;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>{

  final Environment globals = new Environment();
  private Environment environment = globals;
  private final Map<Expr, Integer> locals = new HashMap<>();

  Interpreter(){
    globals.define("clock", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString(){ return "<native fn>";}
    });
  }

  void interpret(List<Stmt> statements){
    try {
      for (Stmt statement : statements) {
        if (statement instanceof Stmt.Expression){
          Object evaluatedExpr = evaluate(((Stmt.Expression) statement).expression);
          System.out.println(stringify(evaluatedExpr));
        } else {
          execute(statement);
        }
      }
    } catch (RuntimeError err){
      Lox.runtimeError(err);
    }
  }
  @Override
  public Object visitLiteralExpr(Expr.Literal expr){
    return expr.value;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr){
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR){
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }
    return evaluate(expr.right);
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr){
    return evaluate(expr.expression);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr){
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG -> {return !isTruthy(right);}
      case MINUS -> {
        checkNumberOperand(expr.operator, right);
        return -(double) right;
      }
    }
    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr){
    return lookUpVariable(expr.name, expr);
  }

  private Object lookUpVariable(Token name, Expr expr){
    Integer distance = locals.get(expr);

    if (distance != null){
      return environment.getAt(distance, name.lexeme);
    } else {
      return globals.get(name);
    }
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr){
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type){
      case GREATER -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left > (double) right;}
      case GREATER_EQUAL -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left >= (double) right;}
      case LESS -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left < (double) right;}
      case LESS_EQUAL -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left <= (double) right;}
      case BANG_EQUAL -> {
        checkNumberOperands(expr.operator, left, right);
        return !isEqual(left, right);}
      case EQUAL_EQUAL -> {
        checkNumberOperands(expr.operator, left, right);
        return isEqual(left, right);}
      case MINUS -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left - (double) right;}
      case PLUS -> {
        if (left instanceof Double && right instanceof Double) {
          return (double) left + (double) right;
        }
        if (left instanceof String && right instanceof String){
          return left + (String) right;
        }

        if (left instanceof String && right instanceof Double){
          return left + stringify(right);
        }

        if (left instanceof Double && right instanceof String) {
          System.out.println(left);
          System.out.println(right);
          return stringify(left) + right;
        }

        throw new RuntimeError(expr.operator, "Operands must both be of the same type (numbers)");
      }
      case SLASH -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left / (double) right; }
      case STAR -> {
        checkNumberOperands(expr.operator, left, right);
        return (double) left * (double) right; }
    }
    return null;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr){
    Object callee = evaluate(expr.callee);

    List<Object> callArgs = new ArrayList<>();
    for (Expr arg : expr.args){
      callArgs.add(evaluate(arg));
    }

    if (!(callee instanceof LoxCallable)){
      throw new RuntimeError(expr.paren, "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable) callee;

    if (callArgs.size() != function.arity()){
      throw new RuntimeError(expr.paren, "Expected "
        + function.arity() + " arguments but got " + callArgs.size() + ".");
    }
    return function.call(this, callArgs);
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function = new LoxFunction(stmt, environment);
    environment.define(stmt.name.lexeme, function);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt){
    if (isTruthy(evaluate(stmt.condition))){
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null){
      execute(stmt.elseBranch);
    }
    return null;
  }

//  @Override
//  public Void visitBreakStmt(Stmt.Break stmt){
//    execute(stmt.skipToStmt);
//    return null;
//  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt){
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);
    throw new Return(value);
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt){
    Object value = null;
    if (stmt.initializer != null){
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt){
    while(isTruthy(evaluate(stmt.condition))){
      execute(stmt.body);
    }
    return null;
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Integer distance = locals.get(expr);
    if (distance != null){
      environment.assignAt(distance, expr.name, value);
    } else {
      globals.assign(expr.name, value);
    }
    return value;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt){
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    return null;
  }

  private Object evaluate(Expr expr){
    return expr.accept(this);
  }

  private void execute(Stmt stmt){
    stmt.accept(this);
  }

  void resolve(Expr expr, int depth){
    locals.put(expr, depth);
  }

  void executeBlock(List<Stmt> statements, Environment env){
    Environment prev = this.environment;

    try {
      this.environment = env;
      for (Stmt statement : statements){
        execute(statement);
      }
    } finally {
      this.environment = prev;
    }
  }

  private boolean isTruthy(Object object){
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean) object;
    return true;
  }

  private boolean isEqual(Object a, Object b){
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private String stringify(Object object){
    if (object == null) return "nil";

    if (object instanceof Double){
      String text = object.toString();

      if (text.endsWith(".0")){
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }
    return object.toString();
  }

  private void checkNumberOperand(Token operator, Object operand){
    if(operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator, Object a, Object b){
    if (a instanceof Double && b instanceof Double) {
     if (operator.type == SLASH && (double) b == 0.0) {
       throw new RuntimeError(operator, "This operand is not able to be used to divide by zero");
     }
     return;
    }
    if (a instanceof String && b instanceof String) return;
    throw new RuntimeError(operator, "Operands must both be of the same type (numbers)");
  }
}
