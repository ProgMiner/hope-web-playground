<script lang="ts">
	import type { Compiler } from '$lib/entities/compiler.svelte';
	import { saveModule } from '$lib/entities/fs/deserialize.svelte';
	import type { ParsedProject } from '$lib/entities/parsed_project.svelte';

	let { project, compiler }: { project: () => ParsedProject | undefined; compiler: Compiler } =
		$props();

	async function exportWasm(): Promise<void> {
		const current = project()?.buildInput();
		if (current) {
			await saveModule(await window.showSaveFilePicker(), compiler, current);
		}
	}
</script>

<div class="flex flex-col p-0.5">
	<button
		onclick={exportWasm}
		class="inline-flex rounded-xs bg-(--editor-background) p-1 font-mono text-sm hover:brightness-(--hover-brightness)"
	>
		Export
	</button>
</div>
