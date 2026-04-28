/**
 * @file Hope grammar for tree-sitter
 * @author Vasily Fedorov <vasek.fedorov@gmail.com>
 * @license MIT
 */

/// <reference types="tree-sitter-cli/dsl" />
// @ts-check

const PREC = {
  BLOCK: 0,
  CASES: 1,
  LET: 2,
  VARIABLE: 3,
  ARGUMENTS: 4,
  BRACKETS: 5,
  EQ: 6,
  ADD: 7,
  MULT: 8,
};

// @ts-ignore
function enumeration(rule) {
  return seq(rule, repeat(seq(",", rule)));
}

export default grammar({
  name: "hope",

  extras: ($) => [$.line_comment, /\s/],

  inline: ($) => [
    $._statement,
    $._primary_expression,
    $._primary_pattern,
    $._primary_type_expression,
  ],

  word: ($) => $.ident,

  rules: {
    compilation_unit: ($) =>
      repeat(seq(choice($.module, $._statement), optional(";"))),

    module: ($) => seq("module", $.binding, repeat($._statement), "end"),

    _statement: ($) =>
      choice(
        $.data_declaration,
        $.function_declaration,
        $.function_equation,
        $.infix_declaration,
        $.type_variable_declaration,
        $.type_export_declaration,
        $.constant_export_declaration,
        $.module_use_declaration,
      ),

    data_declaration: ($) =>
      seq(
        "data",
        $.binding,
        optional($.type_parameters),
        "==",
        $.type_expression,
      ),

    type_parameters: ($) => repeat1($.binding),

    type_expression: ($) =>
      choice(
        $.binary_type_expression,
        prec.left(PREC.ARGUMENTS, repeat1($._primary_type_expression)),
      ),

    _primary_type_expression: ($) =>
      choice($.ident, seq("(", $.type_expression, ")")),

    binary_type_expression: ($) =>
      choice(
        prec.left(PREC.EQ, seq($.type_expression, "->", $.type_expression)),
        prec.left(PREC.ADD, seq($.type_expression, "++", $.type_expression)),
        prec.left(PREC.MULT, seq($.type_expression, "#", $.type_expression)),
      ),

    expression: ($) =>
      prec.left(PREC.ARGUMENTS, repeat1($._primary_expression)),

    _primary_expression: ($) =>
      prec(
        PREC.BRACKETS,
        choice(
          $.decimal,
          $.ident,
          $.string,
          $.char,
          seq("(", $.expression, ")"),
          $.tuple,
          $.list_expression,
          $.set_expression,
          $.conditional_expression,
          $.local_variable_expression,
          $.lambda_expression,
        ),
      ),

    tuple: ($) => seq("(", optional(enumeration($.expression)), ")"),

    list_expression: ($) =>
      prec(PREC.ARGUMENTS, seq("[", optional(enumeration($.expression)), "]")),

    set_expression: ($) =>
      prec(PREC.ARGUMENTS, seq("{", optional(enumeration($.expression)), "}")),

    conditional_expression: ($) =>
      prec.right(
        PREC.LET,
        choice(
          seq($.expression, "then", $.expression, "else", $.expression),
          seq($.expression, "if", $.expression, "else", $.expression),
        ),
      ),

    local_variable_expression: ($) =>
      prec.right(
        PREC.LET,
        choice(
          seq("let", $.pattern, "==", $.expression, "in", $.expression),
          seq($.expression, "where", $.pattern, "==", $.expression),
        ),
      ),

    lambda_expression: ($) =>
      seq(
        "lambda",
        prec.left(
          PREC.CASES,
          seq($.lambda_branch, repeat(seq("|", $.lambda_branch))),
        ),
      ),

    lambda_branch: ($) =>
      prec.left(PREC.CASES, seq($.pattern, "=>", $.expression)),

    function_declaration: ($) =>
      seq("dec", enumeration($.binding), ":", $.type_expression),

    function_equation: ($) => seq("---", $.pattern, "<=", $.expression),

    infix_declaration: ($) =>
      seq(choice("infix", "infixr"), enumeration($.binding), ":", $.decimal),

    type_variable_declaration: ($) => seq("typevar", enumeration($.binding)),

    type_export_declaration: ($) => seq("pubtype", enumeration($.ident)),

    constant_export_declaration: ($) => seq("pubconst", enumeration($.ident)),

    module_use_declaration: ($) => seq("uses", enumeration($.ident)),

    pattern: ($) => prec.left(PREC.ARGUMENTS, repeat1($._primary_pattern)),

    _primary_pattern: ($) =>
      choice(
        $.wildcard_pattern,
        $.array_pattern,
        $.list_pattern,
        $.binding_pattern,
        $.expression,
        seq("(", $.pattern, ")"),
      ),

    binding_pattern: ($) => seq($.binding, "@", $.pattern),
    wildcard_pattern: (_) => "_",
    list_pattern: ($) => seq("{", optional(enumeration($.pattern)), "}"),
    array_pattern: ($) => seq("[", optional(enumeration($.pattern)), "]"),

    binding: ($) => field("name", $.ident),

    ident: (_) => /[a-zA-Z0-9+*/%$#@!|&^?<>:=\-_']+/,
    decimal: (_) => /-?\d+/,
    string: (_) => /"([^\"]|"")*"/,
    char: (_) => /’([^’]|’’|\n|\t)’/,

    line_comment: (_) => token(prec(PREC.BLOCK, seq("!", /[^\n]*/))),
  },
});
