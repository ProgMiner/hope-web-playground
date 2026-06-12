<script lang="ts">
	import { Compiler } from '$lib/entities/compiler.svelte';
	import type { Terminal } from '$lib/entities/terminal.svelte';
	import type { Tree } from 'web-tree-sitter';

<<<<<<< HEAD
	let compiler = new Compiler();
	let { freshTree, terminal }: { freshTree: () => Tree | undefined; terminal: Terminal } =
		$props();
=======
	let {
		tree,
		terminal,
		compiler
	}: { tree: Tree | undefined; terminal: Terminal; compiler: Compiler } = $props();
>>>>>>> master

	async function run(): Promise<void> {
		const tree = freshTree();
		if (!tree) {
			terminal.writeln('No syntax tree to compile');
			return;
		}
		compiler.currentProblems().forEach((problem) => terminal.writeln(problem.message));
		const compiled = await compiler.instantiate();
		if (!compiled) {
			terminal.writeln('Compilation failed');
			return;
		}
		callMain(compiled.exports as Record<string, unknown>);
	}

	function callMain(exports: Record<string, unknown>) {
		if (typeof exports['main'] !== 'function') {
			terminal.writeln('No main function found');
			return;
		}
		terminal.writeln(`${exports['main']()}`);
	}
</script>

<div class="flex flex-col p-0.5">
	<button
		onclick={run}
		class="inline-flex rounded-xs bg-(--editor-background) p-1 font-mono text-sm hover:brightness-(--hover-brightness)"
	>
		Run
	</button>
</div>
