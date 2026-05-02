<script lang="ts">
	import type { MonacoEditor } from '$lib/entities/editor.svelte';
	import { Toasts } from 'svoast';
	import Editor from './Editor.svelte';
	import { onMount } from 'svelte';
	import { Themes } from '$lib/entities/themes.svelte';
	import { setThemes } from '$lib/entities/context';

	let editor: MonacoEditor | undefined = $state();
	const themes = setThemes(new Themes());

	onMount(async () => {
		await themes.load();
	});
</script>

<div>
	<Editor bind:editor />
</div>
<Toasts />

<style lang="postcss">
	@reference "tailwindcss";

	@theme {
		--editor-foreground: #4d4d4c;
		--editor-background: #1e1e1e;
		--editor-background-darker: color-mix(in srgb, var(--editor-background), black 20%);
		--editor-selectionBackground: #d6d6d6;
		--editor-lineHighlightBackground: #efefef;
		--editorCursor-foreground: #aeafad;
		--editorWhitespace-foreground: #d1d1d1;
		--hover-brightness: 150;
	}

	:global(html) {
		background-color: var(--editor-background);
		color: var(--editor-foreground);
		caret-color: var(--editorCursor-foreground);
		scrollbar-color: var(--editor-foreground) var(--editor-background);
	}

	*::selection {
		background: var(--editor-selectionBackground);
	}
</style>
