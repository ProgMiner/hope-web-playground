import { ImaginaryFile } from './file.svelte';
import { ImaginaryFolder } from './folder.svelte';
import { ImaginaryProject } from './project.svelte';
import type { ImaginaryContainer } from './resource.svelte';

interface RawProject {
	type: string;
	name: string;
	root: RawFolder;
}

interface RawFolder {
	type: string;
	name: string;
	children: (RawFolder | RawFile)[];
}

interface RawFile {
	type: string;
	name: string;
	content: string;
}

export function loadProject(raw: string): ImaginaryProject {
	const parsed: RawProject = JSON.parse(raw);
	const project = new ImaginaryProject(parsed.name);
	fillChildren(project, parsed.root);
	return project;
}

function createFolder(parent: ImaginaryContainer, raw: RawFolder): ImaginaryFolder {
	const folder = new ImaginaryFolder(parent, raw.name);
	fillChildren(folder, raw);
	return folder;
}

function fillChildren(folder: ImaginaryContainer, raw: RawFolder) {
	raw.children
		.map((child) => {
			switch (child.type) {
				case 'folder':
					return createFolder(folder, child as RawFolder);
				case 'file':
					return createFile(folder, child as RawFile);
			}
		})
		.forEach((child) => folder.addChild(child!));
}

function createFile(parent: ImaginaryContainer, raw: RawFile): ImaginaryFile {
	return new ImaginaryFile(
		parent,
		raw.name,
		Uint8Array.from(atob(raw.content), (c) => c.charCodeAt(0))
	);
}

export function exemplarPorject(): string {
	return `
  {
    "type": "project",
    "name": "good-hope-project",
    "root": {
      "type": "folder",
      "name": "good-hope-project",
      "children": [
        {
          "type": "file",
          "name": "main.hope",
          "content": "ISBhIGJpbmFyeSB0cmVlIHR5cGUKCmRhdGEgdHJlZSBhbHBoYSA9PSBUaXAgKysgQnJhbmNoKGFscGhhICMgdHJlZSBhbHBoYSAjIHRyZWUgYWxwaGEpOwoKZGVjIGZvbGRfdHJlZSA6IGJldGEgIyAoYWxwaGEgIyBiZXRhICMgYmV0YSAtPiBiZXRhKSAtPgoJCQl0cmVlIGFscGhhIC0+IGJldGE7Ci0tLSBmb2xkX3RyZWUoZSwgbikgVGlwIDw9IGU7Ci0tLSBmb2xkX3RyZWUoZSwgbikgKEJyYW5jaCh4LCBsLCByKSkgPD0KCQluKHgsIGZvbGRfdHJlZShlLCBuKSBsLCBmb2xkX3RyZWUoZSwgbikgcik7CgpkZWMgZmxhdHRlbiA6IHRyZWUgYWxwaGEgLT4gbGlzdCBhbHBoYTsKCmRlYyBzaG93X3RyZWUgOiAoYWxwaGEgLT4gbGlzdCBjaGFyKSAtPiB0cmVlIGFscGhhIC0+IGxpc3QgY2hhcjsKCnByaXZhdGU7CgpkZWMgYXBwZW5kIDogdHJlZSBhbHBoYSAjIGxpc3QgYWxwaGEgLT4gbGlzdCBhbHBoYTsKLS0tIGFwcGVuZChUaXAsIHhzKSA8PSB4czsKLS0tIGFwcGVuZChCcmFuY2goeCwgbCwgciksIHhzKSA8PSBhcHBlbmQobCwgeDo6YXBwZW5kKHIsIHhzKSk7CgotLS0gZmxhdHRlbiB0IDw9IGFwcGVuZCh0LCBbXSk7CgoJCWRlYyBzaG93X3RyZWUnIDogKGFscGhhIC0+IGxpc3QgY2hhcikgLT4KCQkJbGlzdCBjaGFyIC0+IHRyZWUgYWxwaGEgLT4gbGlzdCBjaGFyIC0+IGxpc3QgY2hhcjsKCQktLS0gc2hvd190cmVlJyBzaG93X2VsdCBwcmVmaXggVGlwIHJlc3QgPD0gIiI7CgkJLS0tIHNob3dfdHJlZScgc2hvd19lbHQgcHJlZml4IChCcmFuY2goeCwgbCwgcikpIHJlc3QgPD0KCQkJbGV0IHByZWZpeCcgPT0gIiAgIiA8PiBwcmVmaXggaW4KCQkJc2hvd190cmVlJyBzaG93X2VsdCBwcmVmaXgnIGwgKAoJCQkJcHJlZml4IDw+IHNob3dfZWx0IHggPD4gIlxcbiIgPD4KCQkJCXNob3dfdHJlZScgc2hvd19lbHQgcHJlZml4JyByIHJlc3QKCQkJKTsKCi0tLSBzaG93X3RyZWUgc2hvd19lbHQgdCA8PSBzaG93X3RyZWUnIHNob3dfZWx0ICIiIHQgIiI7Cg=="
        },
        {
          "type": "folder",
          "name": "lib",
          "children": [
            {
              "type": "file",
              "name": "std.hope",
              "content": "ZGF0YSBsID09IGVtcHR5ICsrIGNvbnMobCkK"
            }
          ]
        }
      ]
    }
  }
  `;
}
