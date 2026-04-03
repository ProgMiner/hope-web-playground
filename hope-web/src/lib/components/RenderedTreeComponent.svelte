<script lang="ts">
	import { type RenderedTreeRow } from '$lib/entities/rendered_tree.svelte';

	let { rows }: { rows: RenderedTreeRow[] } = $props();
</script>

<!-- prettier-ignore -->
{#snippet renderedRow(row: RenderedTreeRow)}
    <pre class="tree-row">
{row.indent}{row.field}<a
			class={row.displayed === 'ERROR' || row.displayed.startsWith('MISSING')
				? 'node-link error plain'
				: row.named
					? 'node-link named plain'
					: 'node-link anonymous plain'}
			href="#{row.id}"
			data-id={row.id}
			data-range="{row.start.row},{row.start.column},{row.end.row},{row.end.column}"
		>{row.displayed}</a><span class="position-info"> [{row.start.row}, {row.start.column}] - [{row.end.row}, {row.end.column}]</span></pre>
{/snippet}

{#each rows as row (row.id)}
	{@render renderedRow(row)}
{/each}
