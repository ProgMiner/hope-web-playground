import { Node, type Point, Tree, TreeCursor } from 'web-tree-sitter';
import { emptyNode, emptyTree, type GenericNode, type GenericTree, type Range, type Resource } from './generic_tree';

export interface RenderedTreeRow {
	indent: string;
	field: string;
	id: number;
	start: Point;
	end: Point;
	displayed: string;
	named: boolean;
}

export class TsToTree {
	private traversed = false;
	private readonly stack: GenericNode[] = [];

	constructor(
		private readonly tree: Tree | undefined,
		private readonly resource: Resource | undefined
	) {
	}

	build(): GenericTree {
		const tree = emptyTree(treeSitterType());
		if (!this.tree) {
			return tree;
		}
		this.stack.push(tree.root);
		this.walkTree(this.tree.walk());
		return tree;
	}

	private walkTree(cursor: TreeCursor) {
		while (this.stack.length > 0) {
			if (!this.traversed) {
				const fresh = emptyNode();
				this.currentGeneric().children.push(fresh);
				this.stack.push(fresh);
				this.convert(cursor, this.displayName(cursor));
				this.goDown(cursor);
			} else if (cursor.gotoNextSibling()) {
				this.stack.pop();
				this.traversed = false;
			} else if (cursor.gotoParent()) {
				this.stack.pop();
				this.traversed = true;
			} else {
				break;
			}
		}
	}

	private currentGeneric(): GenericNode {
		return this.stack[this.stack.length - 1];
	}

	private convert(cursor: TreeCursor, displayed: string) {
		if (!displayed) {
			return;
		}
		this.currentGeneric().range = this.range(cursor.currentNode);
		this.currentGeneric().text = this.fieldName(cursor) + displayed;
	}

	private range(node: Node): Range {
		return {
			resource: this.resource,
			from: {
				index: node.startIndex,
				row: node.startPosition.row,
				column: node.startPosition.column
			},
			to: {
				index: node.endIndex,
				row: node.endPosition.row,
				column: node.endPosition.column
			}
		};
	}

	private goDown(cursor: TreeCursor) {
		this.traversed = !cursor.gotoFirstChild();
	}

	private displayName(cursor: TreeCursor): string {
		if (cursor.nodeIsMissing) {
			const nodeTypeText = cursor.nodeIsNamed ? cursor.nodeType : `"${cursor.nodeType}"`;
			return `MISSING ${nodeTypeText}`;
		} else {
			return cursor.nodeType;
		}
	}

	private fieldName(cursor: TreeCursor): string {
		if (cursor.currentFieldName) {
			return cursor.currentFieldName + ': ';
		} else {
			return '';
		}
	}
}

export function treeSitterType(): string {
	return 'TREE_SITTER';
}
