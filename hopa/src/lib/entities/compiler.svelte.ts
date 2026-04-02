import type { Tree } from 'web-tree-sitter';

export class Compiler {
	private readonly memory: WebAssembly.Memory;
	private module: WebAssembly.Instance | undefined;

	constructor(
		private readonly location: string,
		limit: number
	) {
		this.memory = new WebAssembly.Memory({ initial: 0, maximum: limit });
	}

	async load(): Promise<void> {
		console.log('loading...');
		const compiler = await WebAssembly.instantiateStreaming(fetch(this.location), {
			js: { mem: this.memory }
		});
		console.log('loaded');
		this.module = compiler.instance;
	}

	async compile(input: Tree): Promise<WebAssembly.Instance | undefined> {
		const pointer = module.exports.compile(input) as number | undefined;
		if (!pointer) {
			return undefined;
		}
		return await this.instantiateModule(pointer);
	}

	private async instantiateModule(pointer: number) {
		const size = new Int32Array(this.memory.buffer)[pointer / 4];
		const compiled = await WebAssembly.instantiateStreaming(
			new Response(this.memory.buffer.slice(pointer + 4, pointer + 4 + size))
		);
		return compiled.instance;
	}
}
