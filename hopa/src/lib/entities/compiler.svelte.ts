import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';

export class Compiler {
	private readonly run: (input: Tree) => number | undefined;
	private readonly memory: WebAssembly.Memory;

	constructor() {
		this.run = hopec.compile as (input: Tree) => number | undefined;
		this.memory = hopec.memory as WebAssembly.Memory;
		this.memory.grow(1);
	}

	async compile(input: Tree): Promise<WebAssembly.Instance | undefined> {
		const size = this.run(input);
		if (!size) {
			return undefined;
		}
		return await this.instantiateModule(size);
	}

	private async instantiateModule(size: number): Promise<WebAssembly.Instance> {
		const compiled = await WebAssembly.instantiate(this.memory.buffer.slice(0, size));
		return compiled.instance;
	}
}
