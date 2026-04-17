import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	server: {
		fs: {
			allow: ['tree-sitter-hope.wasm', 'hopec-driver/', '../build/wasm/packages/']
		}
	},
	optimizeDeps: {
		exclude: ['web-tree-sitter', 'monaco-editor', 'monaco-themes']
	}
});
