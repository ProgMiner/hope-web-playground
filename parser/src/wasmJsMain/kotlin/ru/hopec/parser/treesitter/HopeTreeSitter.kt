package ru.hopec.parser.treesitter

@OptIn(ExperimentalWasmJsInterop::class)
fun parseHope(location: String, input: String): Tree {
    val parser = Parser()
    val loaded = Language.load(location)
//    parser.setLanguage(loaded)
    return parser.parse(input.toJsString(), oldTree = null, options = null)
}