import { TreeSitter } from './parser.svelte';
import { Highlighting, type HighlightingListener } from './highlighting.svelte';
import type { MonacoEditor } from './editor.svelte';
import type { Tree } from 'web-tree-sitter';

export class ParsedFile {
	private readonly sitter: TreeSitter;
	private readonly highlight: Highlighting;

	constructor(editor: MonacoEditor, text: () => string) {
		this.sitter = new TreeSitter(text);
		this.highlight = new Highlighting(editor, this.sitter);
	}

	init() {
		this.sitter.parse();
	}

	bind() {
		this.highlight.bind();
	}

	unbind() {
		this.highlight.unbind();
	}

	listenTree(listener: HighlightingListener) {
		this.highlight.listenTree(listener);
	}

	currentTree(): Tree | undefined {
		return this.sitter.currentTree();
	}

	dispose() {
		this.highlight.unbind();
		this.sitter.dispose();
	}
}
