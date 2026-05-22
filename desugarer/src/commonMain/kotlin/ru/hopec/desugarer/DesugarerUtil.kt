package ru.hopec.desugarer

import kotlin.collections.component1
import kotlin.collections.component2

fun <T, K : T> MutableMap<String, MutableSet<T>>.uniteSet(map: MutableMap<String, MutableSet<K>>) {
    map.forEach { (name, function) ->
        getOrPut(name) { mutableSetOf() }.addAll(function)
    }
}

fun <T, K : T, U : T> uniteSet(
    map1: MutableMap<String, MutableSet<K>>,
    map2: MutableMap<String, MutableSet<U>>,
): MutableMap<String, MutableSet<T>> {
    val map = mutableMapOf<String, MutableSet<T>>()
    map1.forEach { (name, function) ->
        map.getOrPut(name) { mutableSetOf() }.addAll(function)
    }
    map2.forEach { (name, function) ->
        map.getOrPut(name) { mutableSetOf() }.addAll(function)
    }
    return map
}

fun <T, K : T> MutableMap<String, MutableSet<T>>.unite(map: MutableMap<String, MutableSet<K>>) {
    map.forEach { (name, function) ->
        getOrPut(name) { mutableSetOf() }.addAll(function)
    }
}
