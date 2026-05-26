package ru.hopec.codegen.runtime

/**
 * «Статическая библиотека рантайма» HOPE в виде WAT-исходников.
 *
 * Здесь собраны все вспомогательные функции, которые иначе пришлось бы
 * инлайнить в каждое место использования. Каждая функция эмитится в модуле
 * один раз, а на местах вызова остаётся только `call $rt.…`.
 */
internal object WatRuntime {
    val CLOSURE_TYPE: String =
        """
        (type ${'$'}closure_fn (func (param i32 i32) (result i32)))
        """.trimIndent()

    /**
     * `${'$'}rt.malloc(bytes) → ptr` — bump-allocator.
     * Возвращает текущий `${'$'}heap_ptr` и сдвигает его вверх на [bytes].
     */
    val MALLOC: String =
        """
        (func ${'$'}rt.malloc (param ${'$'}bytes i32) (result i32)
          global.get ${'$'}heap_ptr
          global.get ${'$'}heap_ptr
          local.get ${'$'}bytes
          i32.add
          global.set ${'$'}heap_ptr
        )
        """.trimIndent()

    /** `${'$'}rt.mk_tuple(fst, snd) → ptr` — аллоцирует `[fst, snd]`. */
    val MK_TUPLE: String =
        """
        (func ${'$'}rt.mk_tuple (param ${'$'}fst i32) (param ${'$'}snd i32) (result i32)
          (local ${'$'}ptr i32)
          i32.const 8
          call ${'$'}rt.malloc
          local.tee ${'$'}ptr
          local.get ${'$'}fst
          i32.store offset=0
          local.get ${'$'}ptr
          local.get ${'$'}snd
          i32.store offset=4
          local.get ${'$'}ptr
        )
        """.trimIndent()

    /** `${'$'}rt.mk_cons(field) → ptr` — аллоцирует одноячеечный `[field]`. */
    val MK_CONS: String =
        """
        (func ${'$'}rt.mk_cons (param ${'$'}field i32) (result i32)
          (local ${'$'}ptr i32)
          i32.const 4
          call ${'$'}rt.malloc
          local.tee ${'$'}ptr
          local.get ${'$'}field
          i32.store offset=0
          local.get ${'$'}ptr
        )
        """.trimIndent()

    /**
     * `${'$'}rt.apply(closure, arg) → i32` — косвенный вызов функции замыкания.
     * Индекс функции в таблице хранится по смещению 0.
     */
    val APPLY: String =
        """
        (func ${'$'}rt.apply (param ${'$'}closure i32) (param ${'$'}arg i32) (result i32)
          local.get ${'$'}closure
          local.get ${'$'}arg
          local.get ${'$'}closure
          i32.load offset=0
          call_indirect (type ${'$'}closure_fn)
        )
        """.trimIndent()

    /**
     * `${'$'}rt.set_contains(set, value) → i32` — линейный поиск.
     * Возвращает 1, если найдено, иначе 0.
     */
    val SET_CONTAINS: String =
        """
        (func ${'$'}rt.set_contains (param ${'$'}set i32) (param ${'$'}value i32) (result i32)
          (local ${'$'}cur i32)
          local.get ${'$'}set
          local.set ${'$'}cur
          (block ${'$'}done (result i32)
            (loop ${'$'}walk
              local.get ${'$'}cur
              i32.eqz
              (if
                (then i32.const 0 br ${'$'}done)
              )
              local.get ${'$'}cur
              i32.load offset=0
              local.get ${'$'}value
              i32.eq
              (if
                (then i32.const 1 br ${'$'}done)
              )
              local.get ${'$'}cur
              i32.load offset=4
              local.set ${'$'}cur
              br ${'$'}walk
            )
            unreachable
          )
        )
        """.trimIndent()

    /**
     * `${'$'}rt.set_insert(set, value) → set`. Если [value] уже есть — возвращает
     * исходное множество, иначе аллоцирует prepend-ячейку `[value, next=set]`.
     */
    val SET_INSERT: String =
        """
        (func ${'$'}rt.set_insert (param ${'$'}set i32) (param ${'$'}value i32) (result i32)
          (local ${'$'}cell i32)
          local.get ${'$'}set
          local.get ${'$'}value
          call ${'$'}rt.set_contains
          (if (result i32)
            (then local.get ${'$'}set)
            (else
              i32.const 8
              call ${'$'}rt.malloc
              local.tee ${'$'}cell
              local.get ${'$'}value
              i32.store offset=0
              local.get ${'$'}cell
              local.get ${'$'}set
              i32.store offset=4
              local.get ${'$'}cell
            )
          )
        )
        """.trimIndent()

    /**
     * `${'$'}rt.mk_closure(idx, n_caps) → ptr`
     *
     * Аллоцирует объект замыкания `[func_table_idx: i32, n_caps: i32, …]`
     * размера `8 + n_caps * 4`. Поля захвата записываются на месте вызова
     * через `i32.store offset=…` — сама функция их не трогает.
     *
     */
    val MK_CLOSURE: String =
        """
        (func ${'$'}rt.mk_closure (param ${'$'}idx i32) (param ${'$'}n_caps i32) (result i32)
          (local ${'$'}ptr i32)
          local.get ${'$'}n_caps
          i32.const 4
          i32.mul
          i32.const 8
          i32.add
          call ${'$'}rt.malloc
          local.tee ${'$'}ptr
          local.get ${'$'}idx
          i32.store offset=0
          local.get ${'$'}ptr
          local.get ${'$'}n_caps
          i32.store offset=4
          local.get ${'$'}ptr
        )
        """.trimIndent()

    /**
     * `${'$'}rt.mk_adt(field_count, tag) → ptr`
     *
     * Аллоцирует объект пользовательского ADT-конструктора
     * `[tag: i32, field₀: i32, …]` размера `4 + field_count * 4`.
     * Тег пишется самой функцией, поля — на месте вызова.
     */
    val MK_ADT: String =
        """
        (func ${'$'}rt.mk_adt (param ${'$'}field_count i32) (param ${'$'}tag i32) (result i32)
          (local ${'$'}ptr i32)
          local.get ${'$'}field_count
          i32.const 4
          i32.mul
          i32.const 4
          i32.add
          call ${'$'}rt.malloc
          local.tee ${'$'}ptr
          local.get ${'$'}tag
          i32.store offset=0
          local.get ${'$'}ptr
        )
        """.trimIndent()

    /**
     * Все сниппеты в порядке эмиссии. [CLOSURE_TYPE] обязан идти перед [APPLY]
     * (там ссылка `(type ${'$'}closure_fn)`); [MK_CLOSURE]/[MK_ADT] зависят
     * от [MALLOC].
     */
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
