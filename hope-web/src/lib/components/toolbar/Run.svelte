<script lang="ts">
	import { Compiler } from '$lib/entities/compiler.svelte';
	import type { CompilationInput } from '$lib/entities/hopec';
	import type { Terminal } from '$lib/entities/terminal.svelte';

	let {
		input,
		compiler,
		terminal
	}: { input: () => CompilationInput | undefined; compiler: Compiler; terminal: Terminal } =
		$props();

	async function run(): Promise<void> {
		const trees = input();
		if (!trees) {
			terminal.writeln('No syntax tree to compile');
			return;
		}
		const compiled = await compiler.compile(trees);
		compiler.currentProblems().forEach((problem) => terminal.writeln(problem.message));
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
		if (typeof exports['rt.reset'] === 'function') {
			(exports['rt.reset'] as () => void)();
		}
		const result = exports['main']();
		terminal.writeln('');
		terminal.writeln(`main returned ${result}`);
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
