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
