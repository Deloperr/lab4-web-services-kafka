package com.example.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Expression {
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\s*(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private final String input;
    private int position;

    private Expression(String input) {
        this.input = input;
    }

    static String evaluate(String input) {
        if (input == null || input.length() > 512) {
            throw new IllegalArgumentException();
        }
        Expression expression = new Expression(input);
        BigDecimal result = expression.term();
        while (expression.peek() != '\0') {
            char operator = expression.input.charAt(expression.position++);
            if (operator != '+' && operator != '-') {
                throw new IllegalArgumentException();
            }
            BigDecimal term = expression.term();
            result = operator == '+' ? result.add(term) : result.subtract(term);
        }
        return result.stripTrailingZeros().toPlainString();
    }

    private BigDecimal term() {
        BigDecimal result = number();
        while (peek() == '*' || peek() == '/') {
            char operator = input.charAt(position++);
            BigDecimal operand = number();
            result = operator == '*' ? result.multiply(operand)
                    : result.divide(operand, MathContext.DECIMAL128);
        }
        return result;
    }

    private BigDecimal number() {
        peek();
        Matcher matcher = NUMBER.matcher(input).region(position, input.length());
        if (!matcher.lookingAt()) {
            throw new IllegalArgumentException();
        }
        position = matcher.end();
        return new BigDecimal(matcher.group().replaceAll("\\s+", ""));
    }

    private char peek() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
        if (position == input.length()) {
            return '\0';
        }
        char next = input.charAt(position);
        if (next == '\0') {
            throw new IllegalArgumentException();
        }
        return next;
    }
}
