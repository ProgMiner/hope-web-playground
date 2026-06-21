import { Parser, Language, Tree, Edit, type Range, Node } from 'web-tree-sitter';
import { HighlightInfo, type Highlighted } from './highlight_info.svelte';

import treeSitterWasmUrl from '$lib/../../tree-sitter-hope.wasm?url';

const hope = await initHope();

async function initHope(): Promise<Language> {
	await Parser.init();

	return Language.load(treeSitterWasmUrl);
}

export class TreeSitter {
	private parser: Parser | undefined;
	private tree: Tree | undefined;
	private readonly text: () => string | undefined;

	constructor(text: () => string | undefined) {
		this.parser = new Parser();
		this.parser.setLanguage(hope);
		this.text = text;
	}

	parse(): Range[] {
		const old = this.tree;
		const parsed = this.parser?.parse(this.text() ?? '', old);
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

	freshParse(): Tree | undefined {
		return this.parser?.parse(this.text()!, null) ?? undefined;
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
