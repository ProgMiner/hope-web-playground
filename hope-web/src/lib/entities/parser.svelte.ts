import { Parser, Language, Tree, Edit, type Range, Node } from 'web-tree-sitter';
import { HighlightInfo, type Highlighted } from './highlight_info.svelte';

export class TreeSitter {
	private parser: Parser | undefined;
	private tree: Tree | undefined;
	private text: (() => string) | undefined;
	private version: number = 0;

	async init(text: () => string): Promise<void> {
		await Parser.init();
		const hope = await Language.load('$lib/../../tree-sitter-hope.wasm');
		this.parser = new Parser();
		this.parser.setLanguage(hope);
		this.text = text;
	}

	parse(): Range[] {
		this.version++;
		const old = this.tree;
		const parsed = this.parser?.parse(this.text!(), old);
		if (parsed) {
			this.tree = parsed!;
			return old?.getChangedRanges(this.tree) ?? [];
		}
		return [];
	}

	edit(edits: Edit[]): Range[] {
		edits.forEach((edit) => this.tree?.edit(edit));
		return this.parse();
	}

	currentTree(): Tree | undefined {
		return this.tree;
	}

	currentVersion(): number {
		return this.version;
	}

	highlightingInfo(node: Node): Highlighted[] {
		const language = this.parser?.language;
		if (!language) {
			return [];
		}
		return new HighlightInfo(node, language).build();
	}

	dispose() {
		this.tree?.delete();
		this.parser = undefined;
	}
}
