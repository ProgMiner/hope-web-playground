package ru.hopec.codegen.runtime

internal object WatRuntime {
    val CLOSURE_TYPE: String =
        """
        (type ${'$'}closure_fn (func (param i32 i32) (result i32)))
        """.trimIndent()

    val MALLOC: String =
        """
        (func ${'$'}rt.malloc (param ${'$'}bytes i32) (result i32)
          (global.get ${'$'}heap_ptr)
          (global.set ${'$'}heap_ptr
            (i32.add
              (global.get ${'$'}heap_ptr)
              (local.get ${'$'}bytes))))
        """.trimIndent()

    val MK_TUPLE: String =
        """
        (func ${'$'}rt.mk_tuple (param ${'$'}fst i32) (param ${'$'}snd i32) (result i32)
          (local ${'$'}ptr i32)
          (local.set ${'$'}ptr
            (call ${'$'}rt.malloc
              (i32.const 8)))
          (i32.store offset=0
            (local.get ${'$'}ptr)
            (local.get ${'$'}fst))
          (i32.store offset=4
            (local.get ${'$'}ptr)
            (local.get ${'$'}snd))
          (local.get ${'$'}ptr))
        """.trimIndent()

    val MK_CONS: String =
        """
        (func ${'$'}rt.mk_cons (param ${'$'}field i32) (result i32)
          (local ${'$'}ptr i32)
          (local.set ${'$'}ptr
            (call ${'$'}rt.malloc
              (i32.const 4)))
          (i32.store offset=0
            (local.get ${'$'}ptr)
            (local.get ${'$'}field))
          (local.get ${'$'}ptr))
        """.trimIndent()

    val APPLY: String =
        """
        (func ${'$'}rt.apply (param ${'$'}closure i32) (param ${'$'}arg i32) (result i32)
          (call_indirect (type ${'$'}closure_fn)
            (local.get ${'$'}closure)
            (local.get ${'$'}arg)
            (i32.load offset=0
              (local.get ${'$'}closure))))
        """.trimIndent()

    val SET_CONTAINS: String =
        """
        (func ${'$'}rt.set_contains (param ${'$'}set i32) (param ${'$'}value i32) (result i32)
          (local ${'$'}cur i32)
          (local.set ${'$'}cur
            (local.get ${'$'}set))
          (block ${'$'}done (result i32)
            (loop ${'$'}walk
              (if
                (i32.eqz
                  (local.get ${'$'}cur))
                (then
                  (br ${'$'}done
                    (i32.const 0))))
              (if
                (i32.eq
                  (i32.load offset=0
                    (local.get ${'$'}cur))
                  (local.get ${'$'}value))
                (then
                  (br ${'$'}done
                    (i32.const 1))))
              (local.set ${'$'}cur
                (i32.load offset=4
                  (local.get ${'$'}cur)))
              (br ${'$'}walk))
            (unreachable)))
        """.trimIndent()

    val SET_INSERT: String =
        """
        (func ${'$'}rt.set_insert (param ${'$'}set i32) (param ${'$'}value i32) (result i32)
          (local ${'$'}cell i32)
          (if (result i32)
            (call ${'$'}rt.set_contains
              (local.get ${'$'}set)
              (local.get ${'$'}value))
            (then
              (local.get ${'$'}set))
            (else
              (local.set ${'$'}cell
                (call ${'$'}rt.malloc
                  (i32.const 8)))
              (i32.store offset=0
                (local.get ${'$'}cell)
                (local.get ${'$'}value))
              (i32.store offset=4
                (local.get ${'$'}cell)
                (local.get ${'$'}set))
              (local.get ${'$'}cell))))
        """.trimIndent()

    val MK_CLOSURE: String =
        """
        (func ${'$'}rt.mk_closure (param ${'$'}idx i32) (param ${'$'}n_caps i32) (result i32)
          (local ${'$'}ptr i32)
          (local.set ${'$'}ptr
            (call ${'$'}rt.malloc
              (i32.add
                (i32.mul
                  (local.get ${'$'}n_caps)
                  (i32.const 4))
                (i32.const 8))))
          (i32.store offset=0
            (local.get ${'$'}ptr)
            (local.get ${'$'}idx))
          (i32.store offset=4
            (local.get ${'$'}ptr)
            (local.get ${'$'}n_caps))
          (local.get ${'$'}ptr))
        """.trimIndent()

    val MK_ADT: String =
        """
        (func ${'$'}rt.mk_adt (param ${'$'}field_count i32) (param ${'$'}tag i32) (result i32)
          (local ${'$'}ptr i32)
          (local.set ${'$'}ptr
            (call ${'$'}rt.malloc
              (i32.add
                (i32.mul
                  (local.get ${'$'}field_count)
                  (i32.const 4))
                (i32.const 4))))
          (i32.store offset=0
            (local.get ${'$'}ptr)
            (local.get ${'$'}tag))
          (local.get ${'$'}ptr))
        """.trimIndent()

    val ALL: List<String> =
        listOf(
            MALLOC,
            MK_TUPLE,
            MK_CONS,
            CLOSURE_TYPE,
            APPLY,
            SET_CONTAINS,
            SET_INSERT,
            MK_CLOSURE,
            MK_ADT,
        )
}
