<script lang="ts">
	import { Hopec } from '$lib/entities/hopec';
	import {
		findNode,
		type GenericNode,
		type GenericTree,
		type Point,
		type Range
	} from '$lib/entities/tree/generic_tree';
	import { RenderedTree, type RenderedTreeRow } from '$lib/entities/tree/rendered_tree';
	import { treeSitterType } from '$lib/entities/tree/tree_sitter';
	import { Tabs } from 'bits-ui';
	import { onMount } from 'svelte';
	import { SvelteMap } from 'svelte/reactivity';
	import RenderedTreeComponent from './RenderedTreeComponent.svelte';

	let {
		trees,
		focus,
		scroll = $bindable()
	}: {
		trees: SvelteMap<string, GenericTree>;
		focus: (range: Range) => void;
		scroll: (point: Point) => void;
	} = $props();

	let rendered = $derived.by(() => {
		const fresh: SvelteMap<string, RenderedTreeRow[]> = new SvelteMap();
		trees.forEach((tree, type) => fresh.set(type, new RenderedTree(tree).build()));
		return fresh;
	});
	let positions = $derived.by(() => {
		const fresh: SvelteMap<GenericNode, number> = new SvelteMap();
		rendered.get(selected)?.forEach((row) => fresh.set(row.node, row.index));
		return fresh;
	});
	let hopec = new Hopec();
	let selected = $state(treeSitterType());
	let labels: SvelteMap<string, string> = new SvelteMap();
	let content: HTMLElement | undefined = $state();

	onMount(() => {
		labels.set(treeSitterType(), 'CST');
		labels.set(hopec.statusTreeType(), 'Problems');
		scroll = goTo;
	});

	function goTo(point: Point) {
		const tree = trees.get(selected);
		if (!tree) return;
		const position = positions.get(findNode(tree.root, point));
		if (!position) return;
		content?.scrollTo({
			top: ((position - 5) / positions.size) * content.scrollHeight,
			behavior: 'smooth'
		});
	}
</script>

<div class="flex flex-2 flex-col overflow-auto p-1" bind:this={content}>
	<Tabs.Root bind:value={selected}>
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
		{#each rendered as [type, rows] (type)}
			<Tabs.Content value={type}>
				<RenderedTreeComponent {rows} {focus} />
			</Tabs.Content>
		{/each}
	</Tabs.Root>
</div>
