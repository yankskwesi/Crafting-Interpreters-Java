package lox;

import java.util.Arrays;
import java.util.List;

import static lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}
  private final List<Token> tokens;

  // looking for a way to consolidate all the extra functions that repeat logic
  private final List<TokenType> binaryTokenTypes = Arrays.asList(
    BANG_EQUAL,
    EQUAL,
    GREATER,
    GREATER_EQUAL,
    LESS,
    LESS_EQUAL,
    MINUS,
    PLUS
  );
  private final List<TokenType> operatorTokenTypes = Arrays.asList(
    SLASH,
    STAR,
    BANG
  );
  private int current = 0;

  Parser(List<Token> tokens){
    this.tokens = tokens;
  }

  Expr parse() {
    try {
      return expressionCrunched();
    } catch (ParseError error) {
      return null;
    }
  }

  private Expr expression() {
    return equality();
  }

  private Expr expressionCrunched(){
    Expr expr = primary();

    while (!isAtEnd()) {
      if (match(binaryTokenTypes.toArray(TokenType.values()))){
        Token op = previous();
        Expr right = expressionCrunched();
        return new Expr.Binary(expr, op, right);
      } else if (match(SLASH, STAR)){
        Token op = previous();
        Expr right = expressionCrunched();
        return new Expr.Binary(expr, op, right);
      } else if (match(BANG, MINUS)){
        Token op = previous();
        Expr right = expressionCrunched();
        return new Expr.Unary(op, right);
      }
    }

    return expr;
  }

  private Expr equality(){
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)){
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison(){
    Expr expr = term();

    while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)){
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS, COMMA, TERNARY)){
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)){
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr unary(){
    if (match(BANG, MINUS)){
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return primary();
  }

  private Expr primary(){
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(COMMA)){
      Expr expr = expression();
      consume(COMMA, "expect new statement after expr");
      return new Expr.Grouping(expr);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression");
  }

  private boolean match(TokenType... types) {
    for (TokenType type: types) {
      if (check(type)){
        advance();
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if(check(type)) return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type){
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance(){
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd(){
    return peek().type == EOF;
  }

  private Token peek(){
    return tokens.get(current);
  }

  private Token previous(){
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize(){
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type){
        case CLASS, FUN, VAR, FOR, IF, WHILE, PRINT -> {}
        case RETURN -> {return;}
      }
      advance();
    }
  }
}