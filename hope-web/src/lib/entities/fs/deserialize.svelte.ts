import type { Compiler } from '../compiler.svelte';
import type { CompilationInput } from '../hopec';
import { ImaginaryFile } from './file.svelte';
import { ImaginaryFolder } from './folder.svelte';
import { ImaginaryProject } from './project.svelte';
import type { ImaginaryContainer, ImaginaryResourceType } from './resource.svelte';

interface RawProject {
	type: ImaginaryResourceType;
	name: string;
	root: RawFolder;
}

interface RawFolder {
	type: ImaginaryResourceType;
	name: string;
	children: (RawFolder | RawFile)[];
}

interface RawFile {
	type: ImaginaryResourceType;
	name: string;
	contents: string;
}

export async function loadFromFile(handle: FileSystemFileHandle): Promise<ImaginaryProject> {
	const readable = await handle.getFile();
	return loadProject(JSON.parse(await readable.text()));
}

export async function saveToFile(handle: FileSystemFileHandle, project: ImaginaryProject) {
	const writable = await handle.createWritable();
	await writable.write(JSON.stringify(project.serialize()));
	await writable.close();
}

export async function saveModule(
	handle: FileSystemFileHandle,
	compiler: Compiler,
	input: CompilationInput
) {
	const result = compiler.rawModule(input);
	if (result) {
		const writable = await handle.createWritable();
		await writable.write(result);
		await writable.close();
	}
}

export function loadProject(parsed: RawProject): ImaginaryProject {
	const project = new ImaginaryProject(parsed.name);
	fillChildren(project, parsed.root);
	return project;
}

function createFolder(parent: ImaginaryContainer, raw: RawFolder): ImaginaryFolder {
	const folder = new ImaginaryFolder(parent, raw.name);
	fillChildren(folder, raw);
	return folder;
}

function fillChildren(folder: ImaginaryContainer, raw: RawFolder) {
	raw.children
		.map((child) => {
			switch (child.type) {
				case 'folder':
					return createFolder(folder, child as RawFolder);
				case 'file':
					return createFile(folder, child as RawFile);
			}
		})
		.forEach((child) => folder.addChild(child!));
}

function createFile(parent: ImaginaryContainer, raw: RawFile): ImaginaryFile {
	return new ImaginaryFile(
		parent,
		raw.name,
		Uint8Array.from(atob(raw.contents), (c) => c.charCodeAt(0))
	);
}

const examplesMap = (() => {
	const orig = import.meta.glob<RawProject>('$lib/assets/examples/*.json');

	return Object.keys(orig).reduce(
		(acc, key) => {
			const newKey = key.replace(/^.*\/assets\/examples\//, '');
			if (newKey !== key) {
				acc[newKey] = orig[key];
			}

			return acc;
		},
		{} as Record<string, () => Promise<RawProject>>
	);
})();

export async function exemplarProject(name: string): Promise<RawProject> {
	return examplesMap[`${name}.json`]();
}

export function knownExamples() {
	return [
		{ key: 'syntax', label: 'Basic syntax examples' },
		{ key: 'binary-tree', label: 'Binary tree' },
		{ key: 'fannkuch-redux', label: 'fannkuch-redux benchmark' },
		{ key: 'std', label: 'Standard library' }
	];
}
