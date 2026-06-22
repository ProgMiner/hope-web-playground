import {
	allLeaves,
	zeroPoint,
	type GenericNode,
	type GenericTree,
	type Point,
	type Resource
} from './tree/generic_tree';
import {
	Hopec,
	type CompilationInput,
	type CompilationResult,
	type TranslationUnitRepresentations
} from './hopec';
import { createTerminalIoHost, createWasmImports } from './wasm_imports';
import type { Terminal } from './terminal.svelte';

export type StatusSeverity = 'info' | 'warning' | 'error';

export interface CompilationStatus {
	severity: StatusSeverity;
	from: Point;
	to: Point;
	resource: Resource | undefined;
	message: string;
}

export class Compiler {
	private readonly hopec: Hopec;
	readonly rebuild: (input: CompilationInput) => void;
	private result: CompilationResult | undefined;

	constructor(
		private readonly rebuilt: () => void,
		private readonly terminal: Terminal
	) {
		this.hopec = new Hopec();
		this.rebuild = debounced((input) => this.compile(input));
		this.result = $state();
	}

	async compile(input: CompilationInput): Promise<WebAssembly.Instance | undefined> {
		const size = this.callCompiler(input);
		if (!size) {
			return undefined;
		}
		const { imports, bindMemory } = createWasmImports(createTerminalIoHost(this.terminal));
		const instance = await this.hopec.instantiateModule(size, imports);
		const programMemory = instance.exports.memory;
		if (programMemory instanceof WebAssembly.Memory) {
			bindMemory(programMemory);
		}
		return instance;
	}

	rawModule(input: CompilationInput): ArrayBuffer | undefined {
		const size = this.callCompiler(input);
		if (size) {
			return this.hopec.rawModule(size);
		} else {
			return undefined;
		}
	}

	private callCompiler(input: CompilationInput): number | undefined {
		const result = this.hopec.compile(input);
		this.result = result;
		this.rebuilt();
		if (!result || result.size === 0) {
			return undefined;
		}
		return result.size;
	}

	currentProblems(): CompilationStatus[] {
		return this.statusTrees().flatMap(allLeaves).map(this.status.bind(this));
	}

	private statusTrees(): GenericTree[] {
		return this.currentRepresentations()
			.map((repr) => repr.trees.find((tree) => tree.type === this.hopec.statusTreeType()))
			.filter((tree) => tree)
			.map((tree) => tree!);
	}

	currentRepresentations(): TranslationUnitRepresentations[] {
		return this.result?.representations ?? [];
	}

	private status(problem: GenericNode): CompilationStatus {
		const from = problem.range.from ?? zeroPoint();
		const to = problem.range.to ?? zeroPoint();
		return {
			severity: this.severity(problem),
			from,
			to,
			resource: problem.range.resource,
			message: problem.text
		};
	}

	private severity(problem: GenericNode): StatusSeverity {
		if (problem.text.startsWith(this.hopec.errorPrefix())) {
			return 'error';
		}
		if (problem.text.startsWith(this.hopec.warningPrefix())) {
			return 'warning';
		}
		return 'info';
	}
}

function debounced(callback: (input: CompilationInput) => void) {
	let timeout: number;
	return (input: CompilationInput) => {
		window.clearTimeout(timeout);
		timeout = window.setTimeout(() => callback(input), 300);
	};
}
