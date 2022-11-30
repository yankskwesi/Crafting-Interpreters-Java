package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lox.TokenType.*;
class Scanner {
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int start = 0;
  private int current = 0;
  private int line = 1;

  // keyword mapping

  private static final Map<String, TokenType> keywords;

  static {
    keywords = new HashMap<>();
    keywords.put("and", AND);
    keywords.put("class", CLASS);
    keywords.put("else", ELSE);
    keywords.put("false", FALSE);
    keywords.put("fun", FUN);
    keywords.put("if", FOR);
    keywords.put("nil", NIL);
    keywords.put("or", OR);
    keywords.put("print", PRINT);
    keywords.put("return", RETURN);
    keywords.put("super", SUPER);
    keywords.put("this", THIS);
    keywords.put("true", TRUE);
    keywords.put("var", VAR);
    keywords.put("while", WHILE);
  }

  Scanner(String source) {
    this.source = source;
  }

  List<Token> scanTokens() {
    while (!isAtEnd()) {
      start = current;
      scanToken();
    }
    tokens.add(new Token(EOF, "", null, line));
    return tokens;
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(' -> addToken(LEFT_PAREN);
      case ')' -> addToken(RIGHT_PAREN);
      case '{' -> addToken(LEFT_BRACE);
      case '}' -> addToken(RIGHT_BRACE);
      case ',' -> addToken(COMMA);
      case '.' -> addToken(DOT);
      case '-' -> addToken(MINUS);
      case '+' -> addToken(PLUS);
      case ';' -> addToken(SEMICOLON);
      case '*' -> addToken(STAR);
      case '!' -> addToken(match('=') ? BANG_EQUAL : BANG);
      case '=' -> addToken(match('=') ? EQUAL_EQUAL : EQUAL);
      case '<' -> addToken(match('=') ? LESS_EQUAL : LESS);
      case '>' -> addToken(match('=') ? GREATER_EQUAL : GREATER);
      case '?' -> addToken(match(':') ? TERNARY : IDENTIFIER);
      case '/' -> {
        // comments generally terminate at the end of lines
        if (match('/')) {
          while (peek() != '\n' && !isAtEnd()) advance();
        } else if (match('*')){
          while ((peek() != '/' || peek() != '*') && !isAtEnd()) advance(); // a c style comment handler
        }
        else {
          addToken(SLASH);
        }
      }
      case ' ', '\r', '\t' -> {}
      case '\n' -> line++;
      case '"' -> string();
      default -> {
        if (isDigit(c)) {
          number();
        } else if (isAlpha(c)){
          identifier();
        }
        else {
          Lox.error(line, "Unexpected character. ");
        }
      }
    }
  }

  private char advance() {
    return source.charAt(current++);
  }

  private void addToken(TokenType type){
    addToken(type, null);
  }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line));
  }

  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (source.charAt(current) != expected) return false;
    current++;
    return true;
  }

  private char peek() {
    if (isAtEnd()) return '\0';
    return source.charAt(current);
  }

  private char peekNext(){
    if (isAtEnd()) return '\0';
    return source.charAt(current + 1);
  }

  private void string(){
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') line++;
      advance();
    }

    if (isAtEnd()){
      Lox.error(line, "unterminated string.");
      return;
    }
    advance(); // closing quote
    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private boolean isAlphaNumeric(char c) {
    return isAlpha(c) || isDigit(c);
  }

  private void number() {
    while (isDigit(peek())) advance();

    // if fraction
    if (peek() == '.' && isDigit(peekNext())) {
      advance();
      while (isDigit(peek())) advance();
    }

    addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
  }

  private void identifier() {
    while(isAlphaNumeric(peek())) advance();
    String text = source.substring(start, current);
    TokenType type = keywords.get(text);
    if (type == null) type = IDENTIFIER;
    addToken(IDENTIFIER);
  }
  private boolean isAtEnd() {
    return current >= source.length();
  }
}
