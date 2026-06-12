<script lang="ts">
	import { MonacoEditor } from '$lib/entities/editor.svelte';
	import { Highlighting } from '$lib/entities/highlighting.svelte';
	import { TreeSitter } from '$lib/entities/parser.svelte';
	import { onDestroy, onMount } from 'svelte';
	import type { Tree } from 'web-tree-sitter';
	import Appearance from './Appearance.svelte';
	import Run from './Run.svelte';
	import { Terminal } from '$lib/entities/terminal.svelte';
	import TerminalComponent from './TerminalComponent.svelte';
	import type { ImaginaryProject } from '$lib/entities/fs/project.svelte';
	import {
		exemplarPorject as examplarProject,
		loadProject
	} from '$lib/entities/fs/deserialize.svelte';
	import type { ImaginaryFile } from '$lib/entities/fs/file.svelte';
	import {
		RenderedFileTree,
		type RenderedResourceRow
	} from '$lib/entities/fs/rendered_resource.svelte';
	import FileTree from './fs/FileTree.svelte';
	import { Compiler } from '$lib/entities/compiler.svelte';
	import { TsToTree } from '$lib/entities/tree/tree_sitter';
	import { SvelteMap } from 'svelte/reactivity';
	import type { GenericTree, Range } from '$lib/entities/tree/generic_tree';
	import TreesView from './TreesView.svelte';

	let { editor = $bindable() }: { editor: MonacoEditor | undefined } = $props();
	let value: string = $state('');
	let view: HTMLDivElement | undefined = $state();
	let compiler = new Compiler();
	let highlight: Highlighting | undefined = $state();
	let sitter = new TreeSitter();
	let terminal = new Terminal();
	let files: RenderedResourceRow[] = $state([]);
	let project: ImaginaryProject = $state(loadProject(examplarProject()));
	let opened: ImaginaryFile | undefined = $state();
	let trees: SvelteMap<string, GenericTree> = new SvelteMap();

	onMount(async () => {
		await initEditor();
		await sitter.init(() => editor!.currentContents());
		await initHighlighting();
		rebuildFileTree();
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
		setTree(new TsToTree(t, undefined).build());
		compiler.rebuild(t);
	}

	$effect(() => {
		setTree(compiler.statusTree());
		editor?.updateMarkers(
			compiler.currentProblems()
			// .filter((problem) => problem.resource?.path === opened?.currentPath())
		);
	});

	function setTree(tree: GenericTree | undefined) {
		if (tree) {
			trees.set(tree.type, tree);
		}
	}

	function open(file: ImaginaryFile) {
		opened?.encode(editor?.currentContents() ?? '');
		opened = file;
		value = file.decode();
		editor?.installContent(value);
	}

	function rebuildFileTree() {
		files = new RenderedFileTree(project).build();
	}

	function focus(range: Range) {
		editor?.focusRange(range);
	}
</script>

<div class="flex h-screen flex-col">
	<div class="flex flex-row">
		<Appearance />
<<<<<<< HEAD
		<Run freshTree={() => sitter.freshParse()} {terminal} />
=======
		<Run tree={sitter.currentTree()} {terminal} {compiler} />
>>>>>>> master
	</div>
	<div class="flex flex-1 flex-row overflow-auto">
		<FileTree rows={files} {open} rebuild={rebuildFileTree} />
		<div class="flex flex-4 flex-col overflow-auto">
			<div bind:this={view} id="editor" class="flex flex-2 flex-col"></div>
			<TerminalComponent {terminal} />
		</div>
		<div class="flex flex-2 flex-col overflow-auto p-1">
			<TreesView {trees} {focus} />
		</div>
	</div>
</div>
