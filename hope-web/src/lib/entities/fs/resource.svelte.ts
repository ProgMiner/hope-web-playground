import type { ImaginaryFile } from './file.svelte';

export type ImaginaryResourceType = 'project' | 'folder' | 'file';

export interface ImaginaryResource {
	currentParent(): ImaginaryContainer | undefined;

	setParent(other: ImaginaryContainer): void;

	currentName(): string;

	currentPath(): string;

	rename(other: string): void;

	isContainer(): boolean;

	serialize(): object;

	allFiles(): ImaginaryFile[];
}

export interface ImaginaryContainer extends ImaginaryResource {
	sortedChildren(): ImaginaryResource[];

	addChild(child: ImaginaryResource): void;

	removeChild(child: ImaginaryResource): void;

	expanded(): boolean;

	expand(): void;

	collapse(): void;
}

export function compare(left: ImaginaryResource, right: ImaginaryResource): number {
	if (left.isContainer() && !right.isContainer()) {
		return -1;
	}
	if (!left.isContainer() && right.isContainer()) {
		return 1;
	}
	if (left.currentName() == right.currentName()) {
		return 0;
	}
	return left.currentName() > right.currentName() ? 1 : -1;
}

export function hasChild(container: ImaginaryContainer, name: string): boolean {
	return container.sortedChildren().find((child) => child.currentName() === name) != undefined;
}
