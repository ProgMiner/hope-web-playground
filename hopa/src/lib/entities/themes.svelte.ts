import registry from '../assets/themes/themelist.json';
import { editor } from 'monaco-editor';

class KnownTheme {
	constructor(
		private readonly key: string,
		private readonly name: string
	) {}

	load(): Promise<editor.IStandaloneThemeData> {
		return import(`../assets/themes/${this.name}.json`);
	}

	monacoKey(): string {
		return this.key;
	}

	displayName(): string {
		return this.name;
	}
}

export interface NamedTheme {
	name: string;
	data: editor.IStandaloneThemeData;
}

export class Themes {
	private readonly known: KnownTheme[] = [];
	private selected: KnownTheme = $state(
		new KnownTheme(
			localStorage.getItem('theme_key') ?? 'tomorrow',
			localStorage.getItem('theme_name') ?? 'Tomorrow'
		)
	);
	private css: string = $state('');

	public styles = () => this.css;
	public selectedTheme = () => this.selected.displayName();

	async load(): Promise<void> {
		Object.entries(registry).forEach((theme) =>
			this.known.push(new KnownTheme(theme[0], theme[1]))
		);
		this.known.sort();
		await this.loadTheme(this.selected.displayName());
	}

	themes(): string[] {
		return this.known.map((theme) => theme.displayName());
	}

	async loadTheme(name: string): Promise<void> {
		const matching = this.known.find((theme) => theme.displayName() === name);
		if (matching) {
			localStorage.setItem('theme_key', matching?.monacoKey());
			localStorage.setItem('theme_name', matching?.displayName());
			this.setTheme(matching, await matching.load());
		} else {
			console.log(`no such theme: ${name}`);
		}
	}

	private setTheme(matching: KnownTheme, loaded: editor.IStandaloneThemeData) {
		this.selected = matching;
		this.css = this.generatedCss(loaded);
		editor.defineTheme(matching.monacoKey(), loaded);
		editor.setTheme(matching.monacoKey());
	}

	private generatedCss(theme: editor.IStandaloneThemeData): string {
		return theme.rules.map((rule) => this.generateRule(rule)).join('\n');
	}

	private generateRule(rule: editor.ITokenThemeRule): string {
		const body = [];
		if (rule.background) {
			body.push(`background-color: #${rule.background};`);
		}
		if (rule.fontStyle) {
			body.push(`font-style: ${rule.fontStyle};`);
		}
		if (rule.foreground) {
			body.push(`color: #${rule.foreground};`);
		}
		return `span.${rule.token}{${body.join('')}}`;
	}
}
