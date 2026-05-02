import { ImaginaryFolder } from './folder.svelte';
import { type ImaginaryContainer, type ImaginaryResource } from './resource.svelte';

export class ImaginaryProject {
	private readonly root: ImaginaryFolder;
	private name: string;

	constructor(name: string) {
		this.name = $state(name);
		this.root = new ImaginaryFolder(this, name, true);
	}

	rootFolder(): ImaginaryFolder {
		return this.root;
	}

	currentParent(): undefined {
		return undefined;
	}

	// eslint-disable-next-line @typescript-eslint/no-unused-vars
	setParent(other: ImaginaryContainer) {}

	currentName(): string {
		return this.name;
	}

	rename(other: string): void {
		this.name = other;
		this.root.rename(other);
	}

	isContainer(): boolean {
		return true;
	}

	sortedChildren(): ImaginaryResource[] {
		return this.root.sortedChildren();
	}

	addChild(child: ImaginaryResource) {
		this.root.addChild(child);
	}

	removeChild(child: ImaginaryResource) {
		this.root.removeChild(child);
	}

	expanded() {
		return this.root.expanded();
	}

	expand() {}

	collapse() {}

	serialize() {
		return {
			name: this.name,
			root: this.root.serialize()
		};
	}
}
