import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';
import type { GenericTree } from './generic_tree';

interface CompilationResult {
	size: number;
	representations: GenericTree[];
}

export class Compiler {
	private readonly run: (input: Tree) => CompilationResult | undefined;
	private readonly memory: WebAssembly.Memory;

	constructor() {
		this.run = hopec.compile as (input: Tree) => CompilationResult | undefined;
		this.memory = hopec.memory as WebAssembly.Memory;
		this.memory.grow(1);
	}

	async compile(input: Tree): Promise<WebAssembly.Instance | undefined> {
		const result = this.run(input);
		if (!result) {
			return undefined;
		}

		console.log(result.representations);
		return this.instantiateModule(result.size);
	}

	private async instantiateModule(size: number): Promise<WebAssembly.Instance> {
		const compiled = await WebAssembly.instantiate(this.memory.buffer.slice(0, size));
		return compiled.instance;
	}
}
