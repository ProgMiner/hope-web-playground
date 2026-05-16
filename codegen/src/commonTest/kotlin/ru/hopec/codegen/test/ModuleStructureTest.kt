package ru.hopec.codegen.test

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ModuleStructureTest {
    @Test
    fun `empty program emits module wrapper`() {
        val w = wat(emptyProgram())
        assertTrue(w.trimStart().startsWith("(module"), "Expected WAT to start with (module")
        assertTrue(w.trimEnd().endsWith(")"), "Expected WAT to end with )")
        assertContains(w, "(module")
        assertContains(w, "memory")
        assertContains(w, "global")
    }

    @Test
    fun `runtime functions are always emitted`() {
        val w = wat(emptyProgram())
        assertContains(w, "\$rt.malloc")
        assertContains(w, "\$rt.mk_tuple")
        assertContains(w, "\$rt.mk_cons")
        assertContains(w, "\$rt.apply")
        assertContains(w, "\$rt.set_contains")
        assertContains(w, "\$rt.set_insert")
        assertContains(w, "\$rt.mk_closure")
        assertContains(w, "\$rt.mk_adt")
        assertContains(w, "func \$rt.malloc (param \$bytes i32) (result i32)")
        assertContains(w, "func \$rt.mk_tuple (param \$fst i32) (param \$snd i32) (result i32)")
        assertContains(w, "func \$rt.mk_cons (param \$field i32) (result i32)")
        assertContains(w, "func \$rt.apply (param \$closure i32) (param \$arg i32) (result i32)")
        assertContains(w, "func \$rt.mk_closure (param \$idx i32) (param \$n_caps i32) (result i32)")
        assertContains(w, "func \$rt.mk_adt (param \$field_count i32) (param \$tag i32) (result i32)")
    }

    @Test
    fun `memory and heap pointer are always emitted`() {
        val w = wat(emptyProgram())
        assertContains(w, "memory")
        assertContains(w, "\$heap_ptr")
        assertContains(w, "(memory (export \"memory\") 1)")
        assertContains(w, "(global \$heap_ptr (mut i32) (i32.const 4096))")
    }
}
