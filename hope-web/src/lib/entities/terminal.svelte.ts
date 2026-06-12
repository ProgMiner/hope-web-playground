export class Terminal {
	private contents: string;

	constructor() {
		this.contents = $state('');
	}

	show: () => string = () => this.contents + '$ ';

	write(text: string) {
		this.contents += text;
	}

	writeln(text: string) {
		this.write(text);
		this.write('\n');
	}
}
