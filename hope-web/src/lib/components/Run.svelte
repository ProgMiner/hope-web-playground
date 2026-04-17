<script lang="ts">
	import { Compiler } from '$lib/entities/compiler.svelte';
	import type { Terminal } from '$lib/entities/terminal.svelte';
	import type { Tree } from 'web-tree-sitter';

	let compiler = new Compiler();
	let { tree, terminal }: { tree: Tree | undefined; terminal: Terminal } = $props();

	async function run(): Promise<void> {
		let compiled = await compiler.compile(tree!);
		terminal.write(compiled?.exports.add(2, 2) + '\n');
	}
</script>

<div class="theme-selection flex flex-col p-0.5">
	<button onclick={run} class="inline-flex rounded-xs p-1 font-mono text-sm hover:bg-slate-200">
		Run
	</button>
</div>
