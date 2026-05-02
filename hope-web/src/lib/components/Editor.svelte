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

	let { editor = $bindable() }: { editor: MonacoEditor | undefined } = $props();
	let value: string = $state('');
	let view: HTMLDivElement | undefined = $state();
	let highlight: Highlighting | undefined = $state();
	let sitter = new TreeSitter();
	let terminal = new Terminal();
	let rendered: RenderedTreeRow[] = $state([]);
	let files: RenderedResourceRow[] = $state([]);
	let project: ImaginaryProject = $state(loadProject(examplarProject()));
	let opened: ImaginaryFile | undefined = $state();

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
		rendered = new RenderedTree(t).build();
	}

	function open(file: ImaginaryFile) {
		opened?.encode(value);
		opened = file;
		value = file.decode();
		editor?.installContent(value);
	}

	function rebuildFileTree() {
		files = new RenderedFileTree(project).build();
	}
</script>

<div class="flex h-screen flex-col">
	<div class="flex flex-row">
		<Appearance />
		<Run tree={sitter.currentTree()} {terminal} />
	</div>
	<div class="flex flex-1 flex-row overflow-auto">
		<FileTree rows={files} {open} rebuild={rebuildFileTree} />
		<div class="flex flex-4 flex-col overflow-auto">
			<div bind:this={view} id="editor" class="flex flex-2 flex-col"></div>
			<TerminalComponent {terminal} />
		</div>
		<div class="flex flex-2 flex-col overflow-auto p-1">
			<RenderedTreeComponent rows={rendered}></RenderedTreeComponent>
		</div>
	</div>
</div>
