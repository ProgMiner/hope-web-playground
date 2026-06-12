import type { Tree } from 'web-tree-sitter';
import {
	allLeaves,
	zeroPoint,
	type GenericNode,
	type Point,
	type Resource
} from './tree/generic_tree';
import { Hopec, type CompilationResult } from './hopec';

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
	readonly rebuild: (input: Tree) => void;
	private result: CompilationResult | undefined;

	constructor() {
		this.hopec = new Hopec();
		this.rebuild = debounced((input) => this.compile(input));
		this.result = $state();
	}

<<<<<<< HEAD
	async compile(input: Tree): Promise<WebAssembly.Instance | undefined> {
		const result = this.run(input);
		if (!result || result.size === 0) {
=======
	private compile(input: Tree) {
		this.result = this.hopec.compile(input);
	}

	async instantiate(): Promise<WebAssembly.Instance | undefined> {
		if (!this.result) {
>>>>>>> master
			return undefined;
		}
		return await this.hopec.instantiateModule(this.result.size);
	}

	currentProblems(): CompilationStatus[] {
		const tree = this.statusTree();
		if (!tree) {
			return [];
		}
		return allLeaves(tree).map((problem) => this.status(problem));
	}

	statusTree() {
		return this.result?.representations.find((tree) => tree.type === this.hopec.statusTreeType());
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

function debounced(callback: (input: Tree) => void) {
	let timeout: number;
	return (input: Tree) => {
		window.clearTimeout(timeout);
		timeout = window.setTimeout(() => callback(input), 300);
	};
}
