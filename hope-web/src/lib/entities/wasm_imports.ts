import type { Terminal } from './terminal.svelte';

export interface IoHost {
	print: (text: string) => void;
	readChar: () => number | null;
}

/**
 * HOPE `list char` is a cons-list: each cell points to a tuple (char_code, rest).
 */
export function decodeHopeString(memory: WebAssembly.Memory, listPtr: number): string {
	const view = new DataView(memory.buffer);
	const limit = memory.buffer.byteLength;
	let cur = listPtr;
	let result = '';
	while (cur !== 0) {
		if (cur < 0 || cur + 4 > limit) {
			return result + ' [invalid string pointer]';
		}
		const tuplePtr = view.getUint32(cur, true);
		if (tuplePtr < 0 || tuplePtr + 8 > limit) {
			return result + ' [invalid string pointer]';
		}
		const charCode = view.getUint32(tuplePtr, true);
		const restPtr = view.getUint32(tuplePtr + 4, true);
		result += String.fromCharCode(charCode);
		cur = restPtr;
	}
	return result;
}

export function createWasmImports(host: IoHost): {
	imports: WebAssembly.Imports;
	bindMemory: (memory: WebAssembly.Memory) => void;
} {
	const memoryHolder: { memory?: WebAssembly.Memory } = {};

	return {
		imports: {
			env: {
				print: (ptr: number) => {
					const memory = memoryHolder.memory;
					if (!memory) return;
					host.print(decodeHopeString(memory, ptr));
				},
				getChar: () => host.readChar() ?? -1
			}
		},
		bindMemory: (memory: WebAssembly.Memory) => {
			memoryHolder.memory = memory;
		}
	};
}

export function createTerminalIoHost(terminal: Terminal): IoHost {
	return {
		print: (text) => terminal.write(text),
		readChar: () => null
	};
}
