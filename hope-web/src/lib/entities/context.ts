import { createContext } from 'svelte';
import type { Themes } from './themes.svelte';

export const [getThemes, setThemes] = createContext<Themes>();
