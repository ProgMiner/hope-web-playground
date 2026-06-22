import { editor, MarkerSeverity, Position } from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import type { CompilationStatus } from './compiler.svelte';
import type { Range } from './tree/generic_tree';

class Listeners {
	readonly content: ((e: editor.IModelContentChangedEvent) => void)[] = [];
	readonly cursor: ((e: editor.ICursorPositionChangedEvent) => void)[] = [];
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
		const cursor = this.standalone.onDidChangeCursorPosition(this.onCursorChange.bind(this));
		this.dispose = () => {
			content?.dispose();
			cursor?.dispose();
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

	removeEditListener(listener: (e: editor.IModelContentChangedEvent) => void) {
		const index = this.listeners.content.indexOf(listener);
		if (index != -1) {
			this.listeners.content.splice(index, 1);
		}
	}

	addCursorListener(listener: (e: editor.ICursorPositionChangedEvent) => void) {
		this.listeners.cursor.push(listener);
	}

	onContentChange(e: editor.IModelContentChangedEvent) {
		this.listeners.content.forEach((listener) => listener(e));
	}

	onCursorChange(e: editor.ICursorPositionChangedEvent) {
		if (e.reason == 3) {
			this.listeners.cursor.forEach((listener) => listener(e));
		}
	}

	positionAt(offset: number): Position | undefined {
		return this.standalone.getModel()?.getPositionAt(offset);
	}

	updateDecorations(fresh: editor.IModelDeltaDecoration[]) {
		this.decorations.set(fresh);
	}

	updateMarkers(fresh: CompilationStatus[]) {
		const model = this.standalone.getModel();
		if (!model) {
			return;
		}
		editor.setModelMarkers(
			model,
			'hopec',
			fresh.map((status) => this.markerData(status))
		);
	}

	private markerData(problem: CompilationStatus): editor.IMarkerData {
		return {
			startLineNumber: problem.from.row + 1,
			startColumn: problem.from.column + 1,
			endLineNumber: problem.to.row + 1,
			endColumn: problem.to.column + 1,
			message: problem.message,
			severity: this.severity(problem)
		};
	}

	private severity(problem: CompilationStatus): MarkerSeverity {
		if (problem.severity === 'error') {
			return MarkerSeverity.Error;
		}
		if (problem.severity === 'warning') {
			return MarkerSeverity.Warning;
		}
		return MarkerSeverity.Info;
	}

	currentContents(): string {
		return this.standalone.getValue();
	}

	installContent(text: string) {
		this.standalone.setValue(text);
	}

	focusRange(range: Range) {
		if (!range.from || !range.to) {
			return;
		}
		this.standalone.setSelection({
			startLineNumber: range.from.row + 1,
			startColumn: range.from.column + 1,
			endLineNumber: range.to.row + 1,
			endColumn: range.to.column + 1
		});
	}
}
