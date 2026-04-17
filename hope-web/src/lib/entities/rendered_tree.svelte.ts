import { Tree, TreeCursor, type Point } from 'web-tree-sitter';

export interface RenderedTreeRow {
	indent: string;
	field: string;
	id: number;
	start: Point;
	end: Point;
	displayed: string;
	named: boolean;
}

export class RenderedTree {
	private traversed = false;
	private level = 0;

	constructor(private readonly tree: Tree | undefined) {}

	build(): RenderedTreeRow[] {
		if (!this.tree) {
			return [];
		}
		const cursor = this.tree.walk();
		const rows: RenderedTreeRow[] = [];
		for (let i = 0; ; i++) {
			if (!this.traversed) {
				this.maybeRenderRow(cursor, this.displayName(cursor), rows);
				this.goDown(cursor);
				continue;
			}
			if (cursor.gotoNextSibling()) {
				this.traversed = false;
			} else if (cursor.gotoParent()) {
				this.traversed = true;
				this.level--;
			} else {
				break;
			}
		}
		return rows;
	}

	private maybeRenderRow(cursor: TreeCursor, displayed: string, rows: RenderedTreeRow[]) {
		if (displayed) {
			rows.push(this.renderRow(cursor, displayed));
		}
	}

	private goDown(cursor: TreeCursor) {
		if (cursor.gotoFirstChild()) {
			this.traversed = false;
			this.level++;
		} else {
			this.traversed = true;
		}
	}

	private renderRow(cursor: TreeCursor, displayed: string): RenderedTreeRow {
		return {
			indent: this.indent(),
			field: this.fieldName(cursor),
			id: cursor.nodeId,
			start: cursor.startPosition,
			end: cursor.endPosition,
			displayed: displayed,
			named: cursor.nodeIsNamed
		};
	}

	private indent() {
		return '  '.repeat(this.level);
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
