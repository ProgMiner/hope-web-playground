<script lang="ts">
	import { MonacoEditor } from '$lib/entities/editor.svelte';
	import { Highlighting } from '$lib/entities/highlighting.svelte';
	import { TreeSitter } from '$lib/entities/parser.svelte';
	import { RenderedTree, type RenderedTreeRow } from '$lib/entities/rendered_tree.svelte';
	import { onDestroy, onMount } from 'svelte';
	import RenderedTreeComponent from './RenderedTreeComponent.svelte';
	import type { Tree } from 'web-tree-sitter';
	import Appearance from './Appearance.svelte';
	import Run from './Run.svelte';
	import { Terminal } from '$lib/entities/terminal.svelte';
	import TerminalComponent from './TerminalComponent.svelte';

	let { editor = $bindable() }: { editor: MonacoEditor | undefined } = $props();
	let value: string = $state(
		`
! a binary tree type

data tree alpha == Tip ++ Branch(alpha # tree alpha # tree alpha);

dec fold_tree : beta # (alpha # beta # beta -> beta) ->
			tree alpha -> beta;
--- fold_tree(e, n) Tip <= e;
--- fold_tree(e, n) (Branch(x, l, r)) <=
		n(x, fold_tree(e, n) l, fold_tree(e, n) r);

dec flatten : tree alpha -> list alpha;

dec show_tree : (alpha -> list char) -> tree alpha -> list char;

private;

dec append : tree alpha # list alpha -> list alpha;
--- append(Tip, xs) <= xs;
--- append(Branch(x, l, r), xs) <= append(l, x::append(r, xs));

--- flatten t <= append(t, []);

		dec show_tree' : (alpha -> list char) ->
			list char -> tree alpha -> list char -> list char;
		--- show_tree' show_elt prefix Tip rest <= "";
		--- show_tree' show_elt prefix (Branch(x, l, r)) rest <=
			let prefix' == "  " <> prefix in
			show_tree' show_elt prefix' l (
				prefix <> show_elt x <> "\\n" <>
				show_tree' show_elt prefix' r rest
			);

--- show_tree show_elt t <= show_tree' show_elt "" t "";
	`
	);
	let view: HTMLDivElement | undefined = $state();
	let highlight: Highlighting | undefined = $state();
	let sitter = new TreeSitter();
	let terminal = new Terminal();
	let rendered: RenderedTreeRow[] = $state([]);

	onMount(async () => {
		await initEditor();
		await sitter.init(() => editor!.currentContents());
		await initHighlighting();
	});

	onDestroy(() => {
		highlight?.dispose();
		sitter.dispose();
		editor?.dispose();
	});

	async function initEditor() {
		editor = new MonacoEditor(view!, value);
		await editor.init();
	}

	async function initHighlighting() {
		highlight = new Highlighting(editor!, sitter);
		highlight.listenTree({ notify: updateTree });
		await highlight.init();
	}

	function updateTree(t: Tree) {
		rendered = new RenderedTree(t).build();
	}
</script>

<div class="flex h-screen flex-col">
	<div class="flex flex-row">
		<Appearance />
		<Run tree={sitter.currentTree()} {terminal} />
	</div>
	<div class="flex flex-1 flex-row overflow-auto">
		<div class="flex flex-2 flex-col overflow-auto">
			<div bind:this={view} id="editor" class="flex flex-2 flex-col"></div>
			<TerminalComponent {terminal} />
		</div>
		<div class="flex flex-1 flex-col overflow-auto p-1">
			<RenderedTreeComponent rows={rendered}></RenderedTreeComponent>
		</div>
	</div>
</div>
