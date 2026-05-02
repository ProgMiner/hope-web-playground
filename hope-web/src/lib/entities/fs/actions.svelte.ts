import { ImaginaryFile } from './file.svelte';
import { ImaginaryFolder } from './folder.svelte';
import { hasChild, type ImaginaryContainer, type ImaginaryResource } from './resource.svelte';

export type ResourceActionType = 'New file' | 'New folder' | 'Rename' | 'Delete';

export interface ResourceActionError {
	message: string;
}

export interface ResourceAction {
	type(): ResourceActionType;

	valid(resource: ImaginaryResource): boolean;

	execute(resource: ImaginaryResource, args: object | undefined): ResourceActionError | undefined;
}

export class CreateFileArgs {
	constructor(readonly name: string) {}

	resource(parent: ImaginaryContainer): ImaginaryResource {
		return new ImaginaryFile(parent, this.name, new Uint8Array());
	}
}

export class CreateFile {
	type(): ResourceActionType {
		return 'New file';
	}

	valid(resource: ImaginaryResource): boolean {
		return resource.isContainer();
	}

	execute(resource: ImaginaryResource, args: CreateFileArgs): ResourceActionError | undefined {
		const container = resource as ImaginaryContainer;
		return createResource(container, args.name, args.resource(container));
	}
}

export class CreateFolderArgs {
	constructor(readonly name: string) {}

	resource(parent: ImaginaryContainer): ImaginaryResource {
		return new ImaginaryFolder(parent, this.name);
	}
}

export class CreateFolder {
	type(): ResourceActionType {
		return 'New folder';
	}

	valid(resource: ImaginaryResource): boolean {
		return resource.isContainer();
	}

	execute(resource: ImaginaryResource, args: CreateFolderArgs): ResourceActionError | undefined {
		const container = resource as ImaginaryContainer;
		return createResource(container, args.name, args.resource(container));
	}
}

function createResource(
	container: ImaginaryContainer,
	name: string,
	fresh: ImaginaryResource
): ResourceActionError | undefined {
	if (hasChild(container, name)) {
		return alreadyExists(name);
	}
	if (fresh) {
		container.addChild(fresh);
		container.expand();
	}
	return undefined;
}

export class RenameResourceArgs {
	constructor(readonly name: string) {}
}

export class RenameResource {
	type(): ResourceActionType {
		return 'Rename';
	}

	// eslint-disable-next-line @typescript-eslint/no-unused-vars
	valid(resource: ImaginaryResource): boolean {
		return true;
	}

	execute(resource: ImaginaryResource, args: RenameResourceArgs): ResourceActionError | undefined {
		const parent = resource.currentParent();
		if (parent && hasChild(parent, args.name)) {
			return alreadyExists(args.name);
		}
		resource.rename(args.name);
		return undefined;
	}
}

export class DeleteResource {
	type(): ResourceActionType {
		return 'Delete';
	}

	valid(resource: ImaginaryResource): boolean {
		return resource.currentParent() != undefined;
	}

	// eslint-disable-next-line @typescript-eslint/no-unused-vars
	execute(resource: ImaginaryResource, args: object = {}): ResourceActionError | undefined {
		resource.currentParent()!.removeChild(resource);
		return undefined;
	}
}

function alreadyExists(name: string): ResourceActionError {
	return { message: `File or directory '${name}' already exists` };
}

export function validActions(target: ImaginaryResource): ResourceAction[] {
	return [new CreateFile(), new CreateFolder(), new RenameResource(), new DeleteResource()].filter(
		(action) => action.valid(target)
	);
}
