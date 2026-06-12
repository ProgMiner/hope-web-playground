import { zeroPoint, type GenericNode, type GenericTree, type Point } from './generic_tree';

export interface RenderedTreeRow {
	indent: string;
	id: string;
	start: Point;
	end: Point;
	displayed: string;
}

export class RenderedTree {
	private level = 0;

	constructor(private readonly tree: GenericTree) {}

	build(): RenderedTreeRow[] {
		const cursor = this.tree.root;
		const rows: RenderedTreeRow[] = [];
		this.maybeRenderRow(cursor, rows);
		return rows;
	}

	private maybeRenderRow(cursor: GenericNode, rows: RenderedTreeRow[]) {
		if (cursor.text) {
			rows.push(this.renderRow(cursor, cursor.text));
		}
		this.level++;
		cursor.children.forEach((child) => this.maybeRenderRow(child, rows));
		this.level--;
	}

	private renderRow(cursor: GenericNode, displayed: string): RenderedTreeRow {
		return {
			indent: this.indent(),
			id: `${this.level}-${cursor.range.from?.index}-${cursor.range.to?.index}-${cursor.text}`,
			start: cursor.range.from ?? zeroPoint(),
			end: cursor.range.to ?? zeroPoint(),
			displayed: displayed,
		};
	}

	private indent() {
		return '  '.repeat(this.level);
	}
}
