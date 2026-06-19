<script lang="ts">
	import { saveToFile } from '$lib/entities/fs/deserialize.svelte';
	import type { ImaginaryProject } from '$lib/entities/fs/project.svelte';

	let { project, saving }: { project: () => ImaginaryProject | undefined; saving: () => void } =
		$props();

	async function save(): Promise<void> {
		const current = project();
		saving();
		if (current) {
			await saveToFile(await window.showSaveFilePicker(), current);
		}
	}
</script>

<div class="flex flex-col p-0.5">
	<button
		onclick={save}
		class="inline-flex rounded-xs bg-(--editor-background) p-1 font-mono text-sm hover:brightness-(--hover-brightness)"
	>
		Save
	</button>
</div>
