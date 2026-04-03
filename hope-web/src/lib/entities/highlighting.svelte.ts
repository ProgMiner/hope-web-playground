import { editor, Range } from 'monaco-editor';
import type { MonacoEditor } from './editor.svelte';
import { TreeSitter } from './parser.svelte';
import type Parser from 'tree-sitter';
import { Edit, Tree, Node } from 'web-tree-sitter';

export interface HighlightingListener {
	notify: (tree: Tree) => void;
}

export class Highlighting {
	private readonly listeners: HighlightingListener[];

	constructor(
		private readonly editor: MonacoEditor,
		private readonly parser: TreeSitter
	) {
		this.listeners = [];
	}

	listenTree(listener: HighlightingListener) {
		this.listeners.push(listener);
	}

	async init(): Promise<void> {
		this.parser.parse();
		this.editor.addEditListener((e) => this.edited(e));
		const tree = this.parser.currentTree();
		if (tree) {
			this.highlight(tree.rootNode);
		}
	}

	private edited(e: editor.IModelContentChangedEvent) {
		const ranges = this.parser.edit(e.changes.map((change) => this.parserEdit(change)));
		ranges
			.map((range) =>
				this.parser.currentTree()?.rootNode.descendantForIndex(range.startIndex, range.endIndex)
			)
			.filter((node) => node)
			.forEach((node) => this.highlight(node!));
		this.fireTreeUpdated();
	}

	private parserEdit(change: editor.IModelContentChange): Edit {
		const offset = change.rangeOffset;
		const old = change.range;
		return new Edit({
			startIndex: offset,
			oldEndIndex: offset + change.rangeLength,
			newEndIndex: offset + change.text.length,
			startPosition: this.parserPoint(old.startLineNumber, old.startColumn),
			oldEndPosition: this.parserPoint(old.endLineNumber, old.endColumn),
			newEndPosition: this.pointAt(offset + change.text.length)!
		});
	}

	private pointAt(offset: number): Parser.Point | undefined {
		const point = this.editor.positionAt(offset);
		if (point) {
			return this.parserPoint(point.lineNumber, point.column);
		}
		return undefined;
	}

	private parserPoint(line: number, column: number): Parser.Point {
		return { row: line - 1, column: column - 1 };
	}

	highlight(node: Node) {
		const decorations: editor.IModelDeltaDecoration[] = [];
		this.parser.highlightingInfo(node.tree.rootNode).forEach((highlighted) =>
			decorations.push({
				range: new Range(
					highlighted.node.startPosition.row + 1,
					highlighted.node.startPosition.column + 1,
					highlighted.node.endPosition.row + 1,
					highlighted.node.endPosition.column + 1
				),
				options: { inlineClassName: highlighted.term }
			})
		);
		this.editor.updateDecorations(decorations);
		this.fireTreeUpdated();
	}

	private fireTreeUpdated() {
		const tree = this.parser.currentTree();
		if (tree) {
			this.listeners.forEach((l) => l.notify(tree));
		}
	}

	dispose() {
		this.parser.dispose();
	}
}
