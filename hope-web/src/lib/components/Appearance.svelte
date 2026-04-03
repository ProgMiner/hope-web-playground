<script lang="ts">
	import { getThemes } from '$lib/entities/context';

	const themes = getThemes();
	let theme = $derived(`<style> ${themes.styles()} </style>`);
	let selection: HTMLDivElement | undefined = $state();
	let expanded = $state(false);

	function expand() {
		expanded = true;
		document.addEventListener('click', onClick);
	}

	function onClick(e: PointerEvent) {
		const rect = selection?.getBoundingClientRect();
		if (
			!rect ||
			(e.clientX >= rect.left &&
				e.clientX <= rect.right &&
				e.clientY >= rect.top &&
				e.clientY <= rect.bottom)
		) {
			return;
		}
		collapse();
	}

	function collapse() {
		document.removeEventListener('click', onClick);
		expanded = false;
	}

	async function selectTheme(theme: string): Promise<void> {
		await themes.loadTheme(theme);
		collapse();
	}

	function isSelected(name: string): boolean {
		return themes.selectedTheme() === name;
	}
</script>

<svelte:head>
	{@html theme}
</svelte:head>

<div bind:this={selection} class="theme-selection flex flex-col p-0.5">
	<button onclick={expand} class="inline-flex rounded-xs p-1 font-mono text-sm hover:bg-slate-200">
		Theme
	</button>
	{#if expanded}
		<div class="fixed top-6 z-10 flex h-50 flex-col items-stretch overflow-auto bg-slate-100 p-1">
			{#each themes.themes() ?? [] as theme (theme)}
				<button onclick={async () => await selectTheme(theme)}>
					<div class="font-mono {isSelected(theme) ? 'bg-slate-200' : 'hover:bg-slate-200'}">
						{theme}
					</div>
				</button>
			{/each}
		</div>
	{/if}
</div>
