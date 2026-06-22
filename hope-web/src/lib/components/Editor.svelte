<script lang="ts">
	import { MonacoEditor } from '$lib/entities/editor.svelte';
	import { onDestroy, onMount } from 'svelte';
	import type { Tree } from 'web-tree-sitter';
	import Appearance from './toolbar/Appearance.svelte';
	import Run from './toolbar/Run.svelte';
	import { Terminal } from '$lib/entities/terminal.svelte';
	import TerminalComponent from './TerminalComponent.svelte';
	import type { ImaginaryProject } from '$lib/entities/fs/project.svelte';
	import {
		exemplarProject as examplarProject,
		loadProject
	} from '$lib/entities/fs/deserialize.svelte';
	import type { ImaginaryFile } from '$lib/entities/fs/file.svelte';
	import {
		RenderedFileTree,
		type RenderedResourceRow
	} from '$lib/entities/fs/rendered_resource.svelte';
	import FileTree from './fs/FileTree.svelte';
	import { Compiler, type CompilationStatus } from '$lib/entities/compiler.svelte';
	import { TsToTree } from '$lib/entities/tree/tree_sitter';
	import { SvelteMap } from 'svelte/reactivity';
	import type { GenericTree, Range, Resource } from '$lib/entities/tree/generic_tree';
	import TreesView from './TreesView.svelte';
	import { ParsedProject } from '$lib/entities/parsed_project.svelte';
	import type { TranslationUnitRepresentations } from '$lib/entities/hopec';
	import Save from './toolbar/Save.svelte';
	import Load from './toolbar/Load.svelte';
	import LoadExample from './toolbar/LoadExample.svelte';

	let { editor = $bindable() }: { editor: MonacoEditor | undefined } = $props();
	let value: string = $state('');
	let view: HTMLDivElement | undefined = $state();
	let terminal = new Terminal();
	let compiler = new Compiler(rebuilt, terminal);
	let files: RenderedResourceRow[] = $state([]);
	let project: ImaginaryProject | undefined = $state();
	let opened: ImaginaryFile | undefined = $state();
	let parsed: ParsedProject | undefined = $state();
	let trees: SvelteMap<string, SvelteMap<string, GenericTree>> = new SvelteMap();

	onMount(async () => {
		await initEditor();
		parsed = new ParsedProject(editor!, () => opened);
		openProject(loadProject(await examplarProject('syntax')));
	});

	onDestroy(() => {
		parsed?.closeProject();
		editor?.dispose();
	});

	function openProject(fresh: ImaginaryProject) {
		closeFile();
		opened = undefined;
		editor?.installContent('');
		project = fresh;
		parsed?.openProject(project);
		rebuildFileTree();
	}

	async function initEditor() {
		editor = new MonacoEditor(view!, value);
		await editor.init();
	}

	function updateTree(t: Tree) {
		const resource = opened?.currentResource();
		if (resource) {
			setTree(resource, new TsToTree(t, resource).build());
			rebuild();
		}
	}

	function currentTrees(): SvelteMap<string, GenericTree> {
		const resource = opened?.currentResource();
		if (!resource) {
			return new SvelteMap();
		}
		return trees.get(resource.path) ?? new SvelteMap();
	}

	function rebuild() {
		const input = parsed?.buildInput();
		if (input) {
			compiler.rebuild(input);
		}
	}

	function rebuilt() {
		setTrees(compiler.currentRepresentations());
		updateMarkers(compiler.currentProblems());
	}

	function setTrees(representations: TranslationUnitRepresentations[]) {
		representations.forEach((repr) => repr.trees.forEach((tree) => setTree(repr.resource, tree)));
	}

	function updateMarkers(problems: CompilationStatus[]) {
		editor?.updateMarkers(
			problems.filter((problem) => problem.resource?.path === opened?.currentPath())
		);
	}

	function setTree(resource: Resource, tree: GenericTree) {
		if (!trees.has(resource.path)) {
			trees.set(resource.path, new SvelteMap());
		}
		trees.get(resource.path)!.set(tree.type, tree);
	}

	function open(file: ImaginaryFile) {
		closeFile();
		opened = file;
		value = file.decode();
		editor?.installContent(value);
		bindParsedFile();
	}

	function closeFile() {
		parsed?.currentFile()?.unbind();
		flushCurrentFile();
	}

	function flushCurrentFile() {
		opened?.encode(editor?.currentContents() ?? '');
	}

	function bindParsedFile() {
		parsed?.currentFile()?.listenTree({ notify: updateTree });
		parsed?.currentFile()?.bind();
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
		<Save project={() => project} saving={flushCurrentFile} />
		<Load loaded={openProject} />
		<LoadExample loaded={openProject} current={() => project} />
		<Run input={() => parsed?.buildInput()} {terminal} {compiler} />
	</div>
	<div class="flex flex-1 flex-row overflow-auto">
		<FileTree rows={files} {open} rebuild={rebuildFileTree} />
		<div class="flex flex-4 flex-col overflow-auto">
			<div bind:this={view} id="editor" class="flex flex-2 flex-col"></div>
			<TerminalComponent {terminal} />
		</div>
		<div class="flex flex-2 flex-col overflow-auto p-1">
			<TreesView trees={currentTrees()} {focus} />
		</div>
	</div>
</div>
