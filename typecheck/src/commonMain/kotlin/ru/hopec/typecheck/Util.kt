package ru.hopec.typecheck

import ru.hopec.typecheck.TypedRepresentation.Type

internal infix fun Unit?.join(other: Unit?) =
    if (this == null) {
        null
    } else {
        other
    }

internal fun Collection<Unit?>.joinAll() =
    fold(Unit as Unit?) { acc, cur ->
        acc join cur
    }

internal fun Type.arguments(): Pair<List<Type>, Type> {
    fun Type.go(): Pair<MutableList<Type>, Type> =
        when (this) {
            is Type.Arrow -> {
                val rest = result.go()
                rest.first.add(argument)
                rest
            }

            else -> {
                mutableListOf<Type>() to this
            }
        }

    val args = go()
    args.first.reverse()
    return args
}

internal fun Type.constructorArguments(): Pair<List<Type>, Type.Data>? {
    val (arg, res) = arguments()
    return when (res) {
        is Type.Data -> arg to res
        else -> null
    }
}

internal fun Type.shift(value: Int): Type =
    when (this) {
        is Type.Data -> Type.Data(constructor, args.map { it.shift(value) })
        is Type.Arrow -> Type.Arrow(argument.shift(value), result.shift(value))
        is Type.Variable -> Type.Variable(value + index)
    }

internal fun <T> Collection<T?>.sequence(): List<T>? =
    fold(mutableListOf<T>() as MutableList<T>?) { acc, cur ->
        if (acc == null) {
            null
        } else {
            if (cur == null) {
                null
            } else {
                acc.add(cur)
                acc
            }
        }
    }

internal fun <T> T?.nullAsList(): List<T> =
    if (this == null) {
        listOf()
    } else {
        listOf(this)
    }
