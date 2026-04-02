<script lang="ts">
	import { Compiler } from '$lib/entities/compiler.svelte';
	import { onMount } from 'svelte';

	let compiler = new Compiler('$lib/../../hopec-driver/kotlin/hopec-driver.wasm', 65536);
	let { tree } = $props();

	onMount(async () => {
		await compiler.load();
	});

	async function run(): Promise<void> {
		let compiled = await compiler.compile(tree);
		console.log(compiled?.exports.add(2, 2));
	}
</script>

<div class="theme-selection flex flex-col p-0.5">
	<button onclick={run} class="inline-flex rounded-xs p-1 font-mono text-sm hover:bg-slate-200">
		Run
	</button>
</div>
