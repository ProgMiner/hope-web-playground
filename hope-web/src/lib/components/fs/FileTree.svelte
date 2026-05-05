<script lang="ts">
	import type { ImaginaryFile } from '$lib/entities/fs/file.svelte';
	import type { RenderedResourceRow } from '$lib/entities/fs/rendered_resource.svelte';
	import ClickableFile from './ClickableFile.svelte';

	let {
		rows,
		open,
		rebuild
	}: {
		rows: RenderedResourceRow[];
		open: (file: ImaginaryFile) => void;
		rebuild: () => void;
	} = $props();
</script>

<button class="flex flex-1 flex-col overflow-auto" oncontextmenu={() => false}>
	{#each rows as row (row.id)}
		<ClickableFile {row} {open} {rebuild} />
	{/each}
</button>
