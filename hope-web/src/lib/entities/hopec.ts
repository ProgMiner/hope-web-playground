import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';
import type { GenericTree } from './generic_tree';

export interface CompilationResult {
	size: number;
	representations: GenericTree[];
	instance: WebAssembly.Instance;
}

export class Hopec {
	private readonly memory: WebAssembly.Memory;

	constructor() {
		this.memory = hopec.memory as WebAssembly.Memory;
		this.memory.grow(1);
	}

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
		const compiled = await WebAssembly.instantiate(this.memory.buffer.slice(0, size));
		return compiled.instance;
	}
}
