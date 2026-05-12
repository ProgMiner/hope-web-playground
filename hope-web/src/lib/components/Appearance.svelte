<script lang="ts">
	import { onMount } from 'svelte';
	import { getThemes } from '$lib/entities/context';
	import { DropdownMenu } from 'bits-ui';

	const themes = getThemes();
	let theme = $derived(themes.styles());

	async function selectTheme(theme: string): Promise<void> {
		await themes.loadTheme(theme);
	}

	function isSelected(name: string): boolean {
		return themes.selectedTheme() === name;
	}

	/* eslint svelte/no-unused-svelte-ignore: "off" */
</script>

<svelte:head>
	<!-- svelte-ignore svelte/no_at_html_tags -->
	{@html theme}
</svelte:head>

<DropdownMenu.Root>
	<DropdownMenu.Trigger
		class="rounded-m inline-flex bg-(--editor-background) p-1.5 font-mono text-sm hover:brightness-(--hover-brightness)"
		>Theme</DropdownMenu.Trigger
	>
	<DropdownMenu.Portal>
		<DropdownMenu.Content
			class="h-50 items-stretch justify-center overflow-auto bg-(--editor-background) p-1"
		>
			{#each themes.themes() ?? [] as theme (theme)}
				<DropdownMenu.Item
					class="flex-1 bg-(--editor-background) p-1 font-mono {isSelected(theme)
						? 'brightness-(--hover-brightness)'
						: 'hover:brightness-(--hover-brightness)'}"
				>
					<button onclick={async () => await selectTheme(theme)}>
						{theme}
					</button>
				</DropdownMenu.Item>
			{/each}
		</DropdownMenu.Content>
	</DropdownMenu.Portal>
</DropdownMenu.Root>
