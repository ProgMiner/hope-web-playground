declare module 'wabt' {
	const wabtFactory: () => Promise<unknown>;
	export default wabtFactory;
}

