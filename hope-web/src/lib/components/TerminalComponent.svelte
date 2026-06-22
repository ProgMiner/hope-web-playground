<script lang="ts">
	import type { Terminal } from '$lib/entities/terminal.svelte';
	import { onMount } from 'svelte';

	let { terminal }: { terminal: Terminal } = $props();

	let contents = $derived(terminal.show());
	let area: HTMLTextAreaElement | undefined = $state();

	onMount(() => {
		area?.addEventListener('focus', (e) => {
			e.preventDefault();
			area?.setSelectionRange(contents.length, contents.length);
		});
	});
</script>

<div class="flex w-full flex-1 overflow-auto border-t">
	<textarea class="flex-1 resize-none outline-0" readonly bind:value={contents} bind:this={area}
	></textarea>
</div>
