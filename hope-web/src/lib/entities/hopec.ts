import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';
import type { GenericTree } from './tree/generic_tree';

export interface CompilationResult {
	size: number;
	representations: GenericTree[];
	instance: WebAssembly.Instance;
}

const memory = initializeMemory();

function initializeMemory(): WebAssembly.Memory {
	const memory = hopec.memory as WebAssembly.Memory;
	memory.grow(1);
	return memory;
}

export class Hopec {
	compile(input: Tree): CompilationResult | undefined {
		return (hopec.compile as (input: Tree) => CompilationResult | undefined)(input);
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

	async instantiateModule(size: number): Promise<WebAssembly.Instance> {
		const compiled = await WebAssembly.instantiate(memory.buffer.slice(0, size));
		return compiled.instance;
	}
}
