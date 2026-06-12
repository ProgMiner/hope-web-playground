<script lang="ts">
	import { Hopec } from '$lib/entities/hopec';
	import type { GenericTree, Range } from '$lib/entities/tree/generic_tree';
	import { RenderedTree } from '$lib/entities/tree/rendered_tree';
	import { treeSitterType } from '$lib/entities/tree/tree_sitter';
	import { Tabs } from 'bits-ui';
	import { onMount } from 'svelte';
	import { SvelteMap } from 'svelte/reactivity';
	import RenderedTreeComponent from './RenderedTreeComponent.svelte';

	let { trees, focus }: { trees: SvelteMap<string, GenericTree>; focus: (range: Range) => void } =
		$props();
	let hopec = new Hopec();
	let labels: SvelteMap<string, string> = new SvelteMap();

	onMount(() => {
		labels.set(treeSitterType(), 'CST');
		labels.set(hopec.statusTreeType(), 'Problems');
	});
</script>

<Tabs.Root value={treeSitterType()}>
	<Tabs.List>
		{#each labels as [type, label] (type)}
			<Tabs.Trigger
				value={type}
				class="rounded-m inline-flex bg-(--editor-background) p-1.5 font-mono text-sm hover:brightness-(--hover-brightness)"
			>
				{label}
			</Tabs.Trigger>
		{/each}
	</Tabs.List>
	{#each trees as [type, tree] (type)}
		<Tabs.Content value={type}>
			<RenderedTreeComponent rows={new RenderedTree(tree).build()} {focus}></RenderedTreeComponent>
		</Tabs.Content>
	{/each}
</Tabs.Root>
