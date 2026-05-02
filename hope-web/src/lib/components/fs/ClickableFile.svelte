<script lang="ts">
	import {
		CreateFile,
		CreateFileArgs,
		CreateFolder,
		CreateFolderArgs,
		DeleteResource,
		RenameResource,
		RenameResourceArgs,
		validActions,
		type ResourceActionError,
		type ResourceActionType
	} from '$lib/entities/fs/actions.svelte';
	import type { ImaginaryFile } from '$lib/entities/fs/file.svelte';
	import { INDENT, type RenderedResourceRow } from '$lib/entities/fs/rendered_resource.svelte';
	import { tick } from 'svelte';
	import File from './FileIcon.svelte';
	import FolderClosed from './FolderClosedIcon.svelte';
	import FolderOpen from './FolderOpenIcon.svelte';
	import { toast } from 'svoast';
	import type { ImaginaryFolder } from '$lib/entities/fs/folder.svelte';
	import { ContextMenu } from 'bits-ui';

	let {
		row,
		open,
		rebuild
	}: {
		row: RenderedResourceRow;
		open: (file: ImaginaryFile) => void;
		rebuild: () => void;
	} = $props();
	let file: HTMLDivElement | undefined = $state();
	let action: ResourceActionType | undefined = $state();
	let name: HTMLTextAreaElement | undefined = $state();

	$effect(() => {
		file?.addEventListener('click', toggle);
	});

	async function toggle(event: PointerEvent) {
		event.preventDefault();
		if (row.type === 'file') {
			open(row.origin as ImaginaryFile);
		} else if (row.type === 'open_folder') {
			(row.origin as ImaginaryFolder).collapse();
			rebuild();
		} else if (row.type === 'closed_folder') {
			(row.origin as ImaginaryFolder).expand();
			rebuild();
		}
	}

	async function focusText() {
		await tick().then(() => {
			name?.focus();
			name?.setSelectionRange(0, row.name.length);
		});
	}

	async function selectAction(key: ResourceActionType) {
		action = key;
		if (key === 'Rename' || key === 'New file' || key === 'New folder') {
			await focusText();
		}
		if (key === 'Delete') {
			new DeleteResource().execute(row.origin);
			reset();
		}
	}

	function rename(): ResourceActionError | undefined {
		return new RenameResource().execute(row.origin, new RenameResourceArgs(name!.value.trim()));
	}

	function newFile(): ResourceActionError | undefined {
		return new CreateFile().execute(row.origin, new CreateFileArgs(name!.value.trim()));
	}

	function newFolder(): ResourceActionError | undefined {
		return new CreateFolder().execute(row.origin, new CreateFolderArgs(name!.value.trim()));
	}

	function execute(event: KeyboardEvent, action: () => ResourceActionError | undefined) {
		if (event.key != 'Enter') {
			return;
		}
		const err = action();
		if (err) {
			toast.error(err.message);
			return;
		}
		reset();
	}

	function reset() {
		name = undefined;
		action = undefined;
		rebuild();
	}
</script>

{#snippet oneShotTextArea(action: () => ResourceActionError | undefined, initial: string)}
	<textarea
		bind:this={name}
		onkeydown={(event) => execute(event, action)}
		onblur={reset}
		rows="1"
		spellcheck="false"
		class="box-border resize-none overflow-clip"
		>{initial}
	</textarea>
{/snippet}

<ContextMenu.Root>
	<ContextMenu.Trigger
		class="rounded-m flex flex-row bg-(--editor-background) font-mono text-sm select-none hover:brightness-(--hover-brightness)"
	>
		<pre>{row.indent}</pre>
		<div bind:this={file} class="flex flex-1 flex-row p-1 text-nowrap">
			<div class="p-1">
				{#if row.type == 'file'}
					<File size="14px" color="var(--editor-foreground)" />
				{:else if row.type == 'open_folder'}
					<FolderOpen size="14px" color="var(--editor-foreground)" />
				{:else if row.type == 'closed_folder'}
					<FolderClosed size="14px" color="var(--editor-foreground)" />
				{/if}
			</div>
			{#if action == 'Rename'}
				{@render oneShotTextArea(rename, row.name)}
			{:else}
				{row.name}
			{/if}
		</div>
	</ContextMenu.Trigger>
	<ContextMenu.Portal>
		<ContextMenu.Content class="bg-(--editor-background) p-1">
			{#each validActions(row.origin).map((action) => action.type()) as action (action)}
				<ContextMenu.Item
					class="bg-(--editor-background) p-1 font-mono text-sm select-none hover:brightness-(--hover-brightness)"
				>
					<button onclick={async () => await selectAction(action)}>
						{action}
					</button>
				</ContextMenu.Item>
			{/each}
		</ContextMenu.Content>
	</ContextMenu.Portal>
</ContextMenu.Root>
{#if action == 'New file'}
	<div class="flex flex-row p-1">
		<pre>{row.indent + INDENT}</pre>
		<File size="14px" color="var(--editor-foreground)" />
		{@render oneShotTextArea(newFile, '')}
	</div>
{:else if action == 'New folder'}
	<div class="flex flex-row p-1">
		<pre>{row.indent + INDENT}</pre>
		<FolderClosed size="14px" color="var(--editor-foreground)" />
		{@render oneShotTextArea(newFolder, '')}
	</div>
{/if}
