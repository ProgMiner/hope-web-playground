import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';
import type { GenericTree, Resource } from './tree/generic_tree';

export interface TranslationUnitRepresentations {
	resource: Resource;
	trees: GenericTree[];
}

export interface CompilationResult {
	size: number;
	representations: TranslationUnitRepresentations[];
	instance: WebAssembly.Instance;
}

export interface TranslationUnit {
	resource: Resource;
	tree: Tree;
}

export interface CompilationInput {
	resources: TranslationUnit[];
}

const memory = initializeMemory();

function initializeMemory(): WebAssembly.Memory {
	const memory = hopec.memory as WebAssembly.Memory;
	memory.grow(1);
	return memory;
}

export class Hopec {
	compile(input: CompilationInput): CompilationResult | undefined {
		return (hopec.compile as (input: CompilationInput) => CompilationResult | undefined)(input);
	}

	statusTreeType(): string {
		return (hopec.statusTreeType as () => string)();
	}

	errorPrefix(): string {
		return (hopec.errorPrefix as () => string)();
	}

	warningPrefix(): string {
		return (hopec.warningPrefix as () => string)();
	}

	async instantiateModule(
		size: number,
		imports?: WebAssembly.Imports
	): Promise<WebAssembly.Instance> {
		const compiled = await WebAssembly.instantiate(memory.buffer.slice(0, size), imports);
		return compiled.instance;
	}
}
