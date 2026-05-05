import { compare, type ImaginaryContainer, type ImaginaryResource } from './resource.svelte';

export class ImaginaryFolder {
	private readonly children: ImaginaryResource[] = $state([]);
	private parent: ImaginaryContainer;
	private name: string;
	private open: boolean;

	constructor(parent: ImaginaryContainer, name: string, open: boolean = false) {
		this.parent = $state(parent);
		this.name = $state(name);
		this.open = $state(open);
	}

	currentParent(): ImaginaryContainer {
		return this.parent;
	}

	setParent(other: ImaginaryContainer) {
		this.parent = other;
	}

	currentName(): string {
		return this.name;
	}

	rename(other: string): void {
		this.name = other;
	}

	isContainer(): boolean {
		return true;
	}

	sortedChildren(): ImaginaryResource[] {
		return this.children.toSorted(compare);
	}

	addChild(child: ImaginaryResource) {
		child.currentParent()?.removeChild(child);
		this.children.push(child);
		child.setParent(this);
	}

	removeChild(child: ImaginaryResource) {
		const index = this.children.indexOf(child);
		if (index > -1) {
			this.children.splice(index, 1);
		}
	}

	expanded(): boolean {
		return this.open;
	}

	expand() {
		this.open = true;
	}

	collapse() {
		this.open = false;
	}

	serialize() {
		return {
			name: this.name,
			children: this.sortedChildren().map((child) => child.serialize())
		};
	}
}
