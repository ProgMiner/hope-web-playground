import type { Tree } from 'web-tree-sitter';
import * as hopec from 'hopec-driver';

type GlobalWithWabt = typeof globalThis & {
	wabt?: unknown;
	hopecToBinary?: (module: unknown) => { buffer: ArrayLike<number> };
	hopecWabtPatched?: boolean;
};

type WabtModuleLike = {
	toBinary: (options?: Record<string, unknown>) => { buffer: ArrayLike<number> };
};

type WabtLike = {
	parseWat: (...args: unknown[]) => WabtModuleLike;
};

function patchWabtToBinaryDefaults(globalWithWabt: GlobalWithWabt): void {
	if (globalWithWabt.hopecWabtPatched) {
		return;
	}

	const wabt = globalWithWabt.wabt as WabtLike | undefined;
	if (!wabt) {
		return;
	}

	const originalParseWat = wabt.parseWat.bind(wabt);
	wabt.parseWat = (...args: unknown[]): WabtModuleLike => {
		const module = originalParseWat(...args);
		const originalToBinary = module.toBinary.bind(module);
		module.toBinary = (options?: Record<string, unknown>) =>
			originalToBinary(options ?? { log: false, write_debug_names: true });
		return module;
	};

	globalWithWabt.hopecWabtPatched = true;
}

let wabtInitPromise: Promise<void> | undefined;

async function ensureWabtInitialized(): Promise<void> {
	const globalWithWabt = globalThis as GlobalWithWabt;
	if (globalWithWabt.wabt) {
		patchWabtToBinaryDefaults(globalWithWabt);
		if (!globalWithWabt.hopecToBinary) {
			globalWithWabt.hopecToBinary = (module: unknown) =>
				(module as { toBinary: (options: Record<string, unknown>) => { buffer: ArrayLike<number> } }).toBinary({
					log: false,
					write_debug_names: true
				});
		}
		return;
	}

	if (!wabtInitPromise) {
		wabtInitPromise = (async () => {
			// wabt is provided at runtime via npm deps; typings may be absent in this setup.
			// @ts-ignore
			const wabtFactoryModule = await import('wabt');
			globalWithWabt.wabt = await wabtFactoryModule.default();
			patchWabtToBinaryDefaults(globalWithWabt);
			globalWithWabt.hopecToBinary = (module: unknown) =>
				(module as { toBinary: (options: Record<string, unknown>) => { buffer: ArrayLike<number> } }).toBinary({
					log: false,
					write_debug_names: true
				});
		})();
	}

	await wabtInitPromise;
}

export class Compiler {
	private readonly run: (input: Tree) => number | undefined;
	private readonly memory: WebAssembly.Memory;

	constructor() {
		this.run = hopec.compile as (input: Tree) => number | undefined;
		this.memory = hopec.memory as WebAssembly.Memory;
		this.memory.grow(1);
	}

	async compile(input: Tree): Promise<WebAssembly.Instance | undefined> {
		await ensureWabtInitialized();
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
