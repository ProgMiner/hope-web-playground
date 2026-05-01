<script lang="ts">
	import type { Terminal } from '$lib/entities/terminal.svelte';

	let { terminal }: { terminal: Terminal } = $props();

	let contents = $derived(terminal.show());
</script>

<div class="flex w-full flex-1 overflow-auto" style="border-top: 1px solid black">
	<textarea class="flex-1 resize-none outline-0" readonly bind:value={contents}></textarea>
</div>
