(line_comment) @comment

[
  "module"
  "end"
  "data"
  "if"
  "then"
  "else"
  "let"
  "in"
  "lambda"
  "dec"
  "infix"
  "infixr"
  "typevar"
  "pubtype"
  "pubconst"
  "uses"
] @keyword

(decimal) @number

[
  ":"
  "---"
  "<="
  "|"
  "=>"
  "=="
  "->"
] @operator

[
 "("
 ")"
 "{"
 "}"
 "["
 "]"
] @punctuation.bracket

[
 (string)
 (char)
] @string

[
 "if"
 "then"
 "else"
] @conditional

[
 "uses"
] @include

(ident) @constructor
