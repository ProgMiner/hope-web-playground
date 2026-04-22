package ru.hopec.typecheck

import ru.hopec.typecheck.TypedRepresentation.*
import kotlin.math.max
import kotlin.math.min

class TypecheckingContext(val signature: Signature) {
    data class BoundVariable(val typeVariable: Int, val letRange: Pair<Int, Int>?)

    private var substitution: ArrayList<Type> = arrayListOf()
    private var boundVariables: ArrayList<BoundVariable> = arrayListOf()

    private fun unify(left: Type, right: Type): Unit? {
        val walkedLeft = walk(left)
        val walkedRight = walk(right)
        return when(walkedLeft) {
            is Type.Variable -> {
                when(walkedRight) {
                    is Type.Variable -> {
                        substitution[max(walkedLeft.index, walkedRight.index)] = Type.Variable(
                            min(walkedLeft.index, walkedRight.index)
                        )
                    }
                    else -> substitution[walkedLeft.index]=  walkedRight
                }
            }

            is Type.Function -> {
                when (walkedRight) {
                    is Type.Variable -> {
                        substitution[walkedRight.index] = walkedLeft
                    }

                    is Type.Function -> unify(walkedLeft.argument, walkedRight.argument) join
                            unify(walkedLeft.result, walkedRight.result)

                    else -> null
                }
            }

            else -> TODO()
        }
    }

    private fun walk(type: Type): Type =
        when(type) {
            is Type.Variable -> {
                when(val image = substitution[type.index]) {
                    is Type.Variable -> {
                        if (image.index == type.index) {
                            image
                        } else {
                            walk(image)
                        }
                    }
                    else -> image
                }
            }
            else -> type
        }
}