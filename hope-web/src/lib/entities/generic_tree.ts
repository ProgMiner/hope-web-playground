export interface GenericTree {
	type: string;
	root: GenericNode;
}

export interface GenericNode {
	location: GenericLocation;
	text: string;
	children: GenericNode[];
}

export interface GenericLocation {
	file: string;
	from: number;
	to: number;
}
