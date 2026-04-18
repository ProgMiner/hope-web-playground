<script lang="ts">
	import { Compiler } from '$lib/entities/compiler.svelte';
	import type { Terminal } from '$lib/entities/terminal.svelte';
	import type { Tree } from 'web-tree-sitter';

	let compiler = new Compiler();
	let { tree, terminal }: { tree: Tree | undefined; terminal: Terminal } = $props();

	async function run(): Promise<void> {
		if (!tree) {
			terminal.write('No syntax tree to compile\n');
			return;
		}

		const compiled = await compiler.compile(tree);
		if (!compiled) {
			terminal.write('Compilation failed\n');
			return;
		}

		const exportsObj = compiled.exports as Record<string, unknown>;

		const result = (add as (left: number, right: number) => number)(2, 2);
		terminal.write(`${result}\n`);
	}
</script>

<div class="theme-selection flex flex-col p-0.5">
	<button onclick={run} class="inline-flex rounded-xs p-1 font-mono text-sm hover:bg-slate-200">
		Run
	</button>
</div>
