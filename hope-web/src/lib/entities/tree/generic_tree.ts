export interface GenericTree {
	type: string;
	root: GenericNode;
}

export interface GenericNode {
	range: Range;
	text: string;
	children: GenericNode[];
}

export interface Range {
	resource: Resource | undefined;
	from: Point | undefined;
	to: Point | undefined;
}

export interface Resource {
	path: string;
}

export interface Point {
	index: number;
	row: number;
	column: number;
}

export function zeroPoint(): Point {
	return {
		index: 0,
		row: 0,
		column: 0
	};
}

export function emptyTree(type: string): GenericTree {
	return {
		type: type,
		root: emptyNode()
	};
}

export function emptyNode(): GenericNode {
	return {
		range: emptyRange(),
		text: '',
		children: []
	};
}

export function emptyRange(): Range {
	return {
		resource: undefined,
		from: undefined,
		to: undefined
	};
}

export function allLeaves(tree: GenericTree): GenericNode[] {
	if (empty(tree)) {
		return [];
	}
	const leaves: GenericNode[] = [];
	dfs([tree.root], leaves);
	return leaves;
}

function empty(tree: GenericTree): boolean {
	return tree.root.children.length == 0;
}

function dfs(stack: GenericNode[], leaves: GenericNode[]) {
	while (stack.length != 0) {
		const next = stack.pop()!;
		if (next.children.length == 0) {
			leaves.push(next);
		}
		next.children.forEach((child) => stack.push(child));
	}
}
