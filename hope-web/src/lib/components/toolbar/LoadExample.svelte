<script lang="ts">
	import { exemplarProject, knownExamples, loadProject } from '$lib/entities/fs/deserialize.svelte';
	import type { ImaginaryProject } from '$lib/entities/fs/project.svelte';
	import { DropdownMenu } from 'bits-ui';

	let {
		loaded,
		current
	}: { loaded: (project: ImaginaryProject) => void; current: () => ImaginaryProject | undefined } =
		$props();

	async function selectProject(selected: string): Promise<void> {
		loaded(loadProject(await exemplarProject(selected)));
	}

	function isSelected(name: string): boolean {
		return current()?.currentName() === name;
	}
</script>

<DropdownMenu.Root>
	<DropdownMenu.Trigger
		class="rounded-m inline-flex bg-(--editor-background) p-1.5 font-mono text-sm hover:brightness-(--hover-brightness)"
		>Examples</DropdownMenu.Trigger
	>
	<DropdownMenu.Portal>
		<DropdownMenu.Content
			class="h-50 items-stretch justify-center overflow-auto bg-(--editor-background) p-1"
		>
			{#each knownExamples() ?? [] as project (project.key)}
				<DropdownMenu.Item
					class="flex-1 bg-(--editor-background) p-1 font-mono {isSelected(project.key)
						? 'brightness-(--hover-brightness)'
						: 'hover:brightness-(--hover-brightness)'}"
				>
					<button onclick={async () => await selectProject(project.key)}>
						{project.label}
					</button>
				</DropdownMenu.Item>
			{/each}
		</DropdownMenu.Content>
	</DropdownMenu.Portal>
</DropdownMenu.Root>
