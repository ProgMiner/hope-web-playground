<script lang="ts">
	import type { Range } from '$lib/entities/tree/generic_tree';
	import { type RenderedTreeRow } from '$lib/entities/tree/rendered_tree';

	let {
		rows,
		focus
	}: {
		rows: RenderedTreeRow[];
		focus: (range: Range) => void;
	} = $props();

	function focusRow(row: RenderedTreeRow) {
		focus({ resource: undefined, from: row.start, to: row.end });
	}
</script>

<!-- prettier-ignore -->
{#snippet renderedRow(row: RenderedTreeRow)}
    <button onclick={() => focusRow(row)} class="rounded-m inline-flex bg-(--editor-background) hover:brightness-(--hover-brightness)">
    <pre class="tree-row">
{row.indent}{row.displayed}<span class="position-info"> [{row.start.row}, {row.start.column}] - [{row.end.row}, {row.end.column}]</span></pre>
    </button>
{/snippet}

{#each rows as row (row.id)}
	{@render renderedRow(row)}
{/each}
