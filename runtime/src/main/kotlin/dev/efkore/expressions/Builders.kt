package dev.efkore.expressions

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun property(prop: KProperty1<*, *>): PropertyExpression = PropertyExpression(prop)
fun constant(value: Any?): ConstantExpression = ConstantExpression(value)

fun gt(left: Expression, right: Expression) = BinaryExpression(BinaryOp.GT, left, right)
fun ge(left: Expression, right: Expression) = BinaryExpression(BinaryOp.GE, left, right)
fun lt(left: Expression, right: Expression) = BinaryExpression(BinaryOp.LT, left, right)
fun le(left: Expression, right: Expression) = BinaryExpression(BinaryOp.LE, left, right)
fun eq(left: Expression, right: Expression) = BinaryExpression(BinaryOp.EQ, left, right)
fun ne(left: Expression, right: Expression) = BinaryExpression(BinaryOp.NE, left, right)
fun and(left: Expression, right: Expression) = BinaryExpression(BinaryOp.AND, left, right)
fun or(left: Expression, right: Expression) = BinaryExpression(BinaryOp.OR, left, right)
fun not(operand: Expression) = UnaryExpression(operand)

// Factory functions the compiler plugin emits instead of constructor calls.
fun paramExpr(name: String, type: KClass<*>): ParameterExpression = ParameterExpression(name, type)
fun lambdaExpr(param: ParameterExpression, body: Expression): LambdaExpression = LambdaExpression(param, body)
