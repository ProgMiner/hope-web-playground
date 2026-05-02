import type { ImaginaryFolder } from './folder.svelte';
import type { ImaginaryProject } from './project.svelte';
import type { ImaginaryResource } from './resource.svelte';

export type ResourceType = 'file' | 'closed_folder' | 'open_folder';

export const INDENT = '  ';

export interface RenderedResourceRow {
	indent: string;
	type: ResourceType;
	name: string;
	id: number;
	origin: ImaginaryResource;
}

let id = 0;

export class RenderedFileTree {
	private level = 0;

	constructor(private readonly project: ImaginaryProject | undefined) {}

	build(): RenderedResourceRow[] {
		if (!this.project) {
			return [];
		}
		return this.renderUnder(this.project.rootFolder());
	}

	private renderUnder(current: ImaginaryResource): RenderedResourceRow[] {
		return [this.renderRow(current)].concat(this.maybeRenderChildren(current));
	}

	private maybeRenderChildren(current: ImaginaryResource): RenderedResourceRow[] {
		if (!current.isContainer()) {
			return [];
		}
		const container = current as ImaginaryFolder;
		if (!container.expanded()) {
			return [];
		}
		return this.renderChildren(container);
	}

	private renderChildren(container: ImaginaryFolder) {
		this.level++;
		const descendants = container.sortedChildren().flatMap((child) => this.renderUnder(child));
		this.level--;
		return descendants;
	}

	private renderRow(cursor: ImaginaryResource): RenderedResourceRow {
		return {
			indent: this.indent(),
			type: this.icon(cursor),
			name: cursor.currentName(),
			id: id++,
			origin: cursor
		};
	}

	private indent() {
		return INDENT.repeat(this.level);
	}

	private icon(cursor: ImaginaryResource): ResourceType {
		if (!cursor.isContainer()) {
			return 'file';
		}
		return (cursor as ImaginaryFolder).expanded() ? 'open_folder' : 'closed_folder';
	}
}
