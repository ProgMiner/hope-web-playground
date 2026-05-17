package ru.hopec.desugarer

import kotlin.collections.component1
import kotlin.collections.component2

fun <T, K : T> MutableMap<String, MutableSet<T>>.uniteSet(map: MutableMap<String, MutableSet<K>>) {
    map.forEach { (name, function) ->
        getOrPut(name) { mutableSetOf() }.addAll(function)
    }
}

fun <T, K : T> MutableMap<String, MutableSet<T>>.unite(map: MutableMap<String, MutableSet<K>>) {
    map.forEach { (name, function) ->
        getOrPut(name) { mutableSetOf() }.addAll(function)
    }
}
