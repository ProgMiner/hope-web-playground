import { SvelteMap } from 'svelte/reactivity';
import type { CompilationInput, TranslationUnit } from './hopec';
import { ParsedFile } from './parsed_file.svelte';
import type { ImaginaryFile } from './fs/file.svelte';
import type { MonacoEditor } from './editor.svelte';
import type { ImaginaryProject } from './fs/project.svelte';
import { stdModules, stdName } from './std';

export class ParsedProject {
	private readonly resources: SvelteMap<ImaginaryFile, ParsedFile> = new SvelteMap();

	constructor(
		private readonly editor: MonacoEditor,
		private readonly opened: () => ImaginaryFile | undefined
	) {}

	createFile(file: ImaginaryFile) {
		if (!this.resources.get(file)) {
			const parsed = new ParsedFile(this.editor, this.currentText(file));
			parsed.parse();
			this.resources.set(file, parsed);
		}
	}

	createAllFiles(project: ImaginaryProject) {
		project.allFiles().forEach((file) => this.createFile(file));
	}

	closeProject() {
		this.resources
			.keys()
			.toArray()
			.forEach((file) => this.deleteFile(file));
	}

	private currentText(file: ImaginaryFile) {
		return () => {
			if (this.opened() == file) {
				return this.editor.currentContents();
			}
			return file.decode();
		};
	}

	currentFile(): ParsedFile | undefined {
		if (!this.opened()) {
			return undefined;
		}
		return this.resources.get(this.opened()!);
	}

	deleteFile(file: ImaginaryFile) {
		this.resources.get(file)?.dispose();
		this.resources.delete(file);
	}

	buildInput(): CompilationInput {
		const all = [this.localUnits()];
		if (this.opened()?.currentName() != stdName()) {
			all.push(stdModules(this.editor));
		}
		return { resources: all.flat() };
	}

	private localUnits() {
		return this.resources
			.entries()
			.map(([file, parsed]) => this.translationUnit(file, parsed))
			.filter((unit) => unit)
			.map((unit) => unit!)
			.toArray();
	}

	private translationUnit(file: ImaginaryFile, parsed: ParsedFile): TranslationUnit | undefined {
		const tree = parsed.currentTree();
		if (!tree) {
			return undefined;
		}
		return { resource: file.currentResource(), tree: tree };
	}
}
