import { Node, Language, Query } from 'web-tree-sitter';

export interface Highlighted {
	term: string;
	node: Node;
}

export class HighlightInfo {
	private readonly info: Highlighted[];

	constructor(
		private readonly node: Node,
		private readonly language: Language
	) {
		this.info = [];
	}

	build(): Highlighted[] {
		const query = new Query(this.language, SYNTAX);
		const captures = query.captures(this.node);
		captures.forEach((capture) =>
			this.info.push({
				term: capture.name,
				node: capture.node
			})
		);
		return this.info;
	}
}

const SYNTAX: string = `
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

  (decimal) @constant.numeric

  [
    ":"
    "---"
    "<="
    "|"
    "=>"
    "=="
    "->"
  ] @keyword.operator

  [
   "("
   ")"
   "{"
   "}"
   "["
   "]"
  ] @constant.character

  [
   (string)
   (char)
  ] @string

  [
   "uses"
  ] @keyword.control.import

  ("uses" (ident) @string.other.link)

  (ident) @constructor`;
