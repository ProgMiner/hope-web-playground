import type { MonacoEditor } from './editor.svelte';
import { exemplarProject, loadProject } from './fs/deserialize.svelte';
import type { ImaginaryFile } from './fs/file.svelte';
import type { TranslationUnit } from './hopec';
import { ParsedFile } from './parsed_file.svelte';

const std = loadProject(await exemplarProject('std'));

export function stdModules(editor: MonacoEditor): TranslationUnit[] {
	return std.allFiles().map((file) => translationUnit(editor, file));
}

function translationUnit(editor: MonacoEditor, file: ImaginaryFile): TranslationUnit {
	const parsed = new ParsedFile(editor, () => file.decode());
	parsed.parse();
	return {
		resource: file.currentResource(),
		tree: parsed.currentTree()!
	};
}
