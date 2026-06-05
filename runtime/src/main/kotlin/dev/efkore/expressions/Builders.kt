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

fun paramExpr(name: String, type: KClass<*>): ParameterExpression = ParameterExpression(name, type)
fun lambdaExpr(param: ParameterExpression, body: Expression): LambdaExpression = LambdaExpression(param, body)

fun count(source: Expression) = CountExpression(source)
fun sum(source: Expression, selector: LambdaExpression) = SumExpression(source, selector)
fun avg(source: Expression, selector: LambdaExpression) = AvgExpression(source, selector)
fun min(source: Expression, selector: LambdaExpression) = MinExpression(source, selector)
fun max(source: Expression, selector: LambdaExpression) = MaxExpression(source, selector)
fun startsWith(source: Expression, value: ConstantExpression) = StartsWithExpression(source, value)
fun contains(source: Expression, value: ConstantExpression) = ContainsExpression(source, value)
fun endsWith(source: Expression, value: ConstantExpression) = EndsWithExpression(source, value)
fun thenBy(source: Expression, selector: LambdaExpression, descending: Boolean = false) = ThenByExpression(source, selector, descending)
fun find(entityType: KClass<*>, keyValues: Map<String, Any?>) = FindExpression(entityType, keyValues)

fun stringStartsWith(target: Expression, arg: ConstantExpression) = StartsWithExpression(target, arg)
fun stringEndsWith(target: Expression, arg: ConstantExpression) = EndsWithExpression(target, arg)
fun stringContains(target: Expression, arg: ConstantExpression) = ContainsExpression(target, arg)
fun isNullPred(property: Expression) = IsNullExpression(property)
fun isNotNullPred(property: Expression) = IsNotNullExpression(property)
