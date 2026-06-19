<script lang="ts">
	import { loadFromFile } from '$lib/entities/fs/deserialize.svelte';
	import type { ImaginaryProject } from '$lib/entities/fs/project.svelte';

	let { loaded }: { loaded: (project: ImaginaryProject) => void } = $props();

	async function load(): Promise<void> {
		const [handle] = await window.showOpenFilePicker();
		loaded(await loadFromFile(handle));
	}
</script>

<div class="flex flex-col p-0.5">
	<button
		onclick={load}
		class="inline-flex rounded-xs bg-(--editor-background) p-1 font-mono text-sm hover:brightness-(--hover-brightness)"
	>
		Load
	</button>
</div>
