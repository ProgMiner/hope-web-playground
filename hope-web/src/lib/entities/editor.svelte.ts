import { editor, Position } from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

class Listeners {
	readonly content: ((e: editor.IModelContentChangedEvent) => void)[] = [];
}

export class MonacoEditor {
	private readonly standalone: editor.IStandaloneCodeEditor;
	private readonly decorations: editor.IEditorDecorationsCollection;
	private readonly listeners: Listeners = new Listeners();
	public dispose: () => void;

	constructor(
		private readonly html: HTMLElement,
		contents: string
	) {
		self.MonacoEnvironment = {
			// eslint-disable-next-line @typescript-eslint/no-unused-vars
			getWorker(id, label: string) {
				return new editorWorker();
			}
		};
		this.standalone = editor.create(html, {
			value: contents,
			theme: 'vs-dark'
		});
		this.decorations = this.standalone.createDecorationsCollection();
		const content = this.standalone.onDidChangeModelContent(this.onContentChange.bind(this));
		this.dispose = () => {
			content?.dispose();
			this.standalone.dispose();
		};
	}

	async init(): Promise<void> {
		this.resize();
	}

	resize() {
		this.standalone.layout({ height: this.html.clientHeight, width: this.html.clientWidth });
	}

	addEditListener(listener: (e: editor.IModelContentChangedEvent) => void) {
		this.listeners.content.push(listener);
	}

	onContentChange(e: editor.IModelContentChangedEvent) {
		this.listeners.content.forEach((listener) => listener(e));
	}

	positionAt(offset: number): Position | undefined {
		return this.standalone.getModel()?.getPositionAt(offset);
	}

	updateDecorations(fresh: editor.IModelDeltaDecoration[]) {
		this.decorations.set(fresh);
	}

	currentContents(): string {
		return this.standalone.getValue();
	}

	installContent(text: string) {
		this.standalone.setValue(text);
	}
}
