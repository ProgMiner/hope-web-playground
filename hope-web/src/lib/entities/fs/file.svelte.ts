import { type ImaginaryContainer } from './resource.svelte';

export class ImaginaryFile {
	private contents: Uint8Array;
	private parent: ImaginaryContainer;
	private name: string;

	constructor(parent: ImaginaryContainer, name: string, contents: Uint8Array = new Uint8Array()) {
		this.parent = $state(parent);
		this.name = $state(name);
		this.contents = contents;
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
		return false;
	}

	encode(text: string) {
		const buffer = new Uint8Array(text.length * 2);
		const written = new TextEncoder().encodeInto(text, buffer).written;
		this.contents = buffer.slice(0, written);
	}

	decode() {
		return new TextDecoder().decode(this.contents);
	}

	serialize() {
		return {
			name: this.name,
			contents: btoa(Array.from(this.contents, (byte) => String.fromCodePoint(byte)).join(''))
		};
	}
}
