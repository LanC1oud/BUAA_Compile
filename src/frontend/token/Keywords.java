package frontend.token;

import frontend.token.TokenType;

import java.util.Map;
import static java.util.Map.entry;

public final class Keywords {
    private Keywords() {}
    public static final Map<String, TokenType> TABLE = Map.ofEntries(
            entry("const", TokenType.Const), entry("int", TokenType.Int),
            entry("break", TokenType.Break), entry("continue", TokenType.Continue),
            entry("if", TokenType.If), entry("else", TokenType.Else),
            entry("while", TokenType.While), entry("return", TokenType.Return),
            entry("void", TokenType.Void), entry("main", TokenType.Main),
            entry("printf", TokenType.Printf), entry("char", TokenType.Char),
            entry("default", TokenType.Default), entry("static", TokenType.Static),
            entry("case", TokenType.Case), entry("switch", TokenType.Switch)
            );
}