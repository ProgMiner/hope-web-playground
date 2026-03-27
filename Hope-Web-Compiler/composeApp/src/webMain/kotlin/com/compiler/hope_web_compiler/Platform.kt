package com.compiler.hope_web_compiler

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform