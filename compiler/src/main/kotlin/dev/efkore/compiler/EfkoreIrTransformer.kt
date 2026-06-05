@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "OPT_IN_USAGE_ERROR", "OPT_IN_USAGE")

package dev.efkore.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val DBSET_FQ = FqName("dev.efkore.DbSet")
private val EXPR_PKG = FqName("dev.efkore.expressions")

class UnsupportedQueryExpression(msg: String) : Exception(msg)

class EfkoreIrTransformer(private val ctx: IrPluginContext) : IrElementTransformerVoid() {

    // ─── symbol helpers ───────────────────────────────────────────────────────

    private fun fn(pkg: FqName, name: String): IrSimpleFunctionSymbol =
        ctx.referenceFunctions(CallableId(pkg, Name.identifier(name))).single()

    private fun cls(fq: FqName): IrClassSymbol =
        ctx.referenceClass(ClassId.topLevel(fq))!!

    private val expressionType get() = cls(FqName("dev.efkore.expressions.Expression")).defaultType

    private val fnProperty        get() = fn(EXPR_PKG, "property")
    private val fnConstant        get() = fn(EXPR_PKG, "constant")
    private val fnGt              get() = fn(EXPR_PKG, "gt")
    private val fnGe              get() = fn(EXPR_PKG, "ge")
    private val fnLt              get() = fn(EXPR_PKG, "lt")
    private val fnLe              get() = fn(EXPR_PKG, "le")
    private val fnEq              get() = fn(EXPR_PKG, "eq")
    private val fnNe              get() = fn(EXPR_PKG, "ne")
    private val fnAnd             get() = fn(EXPR_PKG, "and")
    private val fnOr              get() = fn(EXPR_PKG, "or")
    private val fnNot             get() = fn(EXPR_PKG, "not")
    private val fnParamExpr       get() = fn(EXPR_PKG, "paramExpr")
    private val fnLambdaExpr      get() = fn(EXPR_PKG, "lambdaExpr")
    private val fnStringStartsWith get() = fn(EXPR_PKG, "stringStartsWith")
    private val fnStringEndsWith   get() = fn(EXPR_PKG, "stringEndsWith")
    private val fnStringContains   get() = fn(EXPR_PKG, "stringContains")
    private val fnIsNullPred       get() = fn(EXPR_PKG, "isNullPred")
    private val fnIsNotNullPred    get() = fn(EXPR_PKG, "isNotNullPred")

    private val dbSetClassId = ClassId(FqName("dev.efkore"), Name.identifier("DbSet"))

    private fun dbSetMethod(name: String): IrSimpleFunctionSymbol =
        ctx.referenceFunctions(CallableId(dbSetClassId, Name.identifier(name))).single()

    // ─── call factory (bypasses internal constructor) ─────────────────────────

    private fun irCallImpl(startOffset: Int, endOffset: Int, type: IrType, symbol: IrSimpleFunctionSymbol): IrCallImpl =
        IrCallImpl(null, startOffset, endOffset, type, null, symbol, null)

    // ─── visitor entry ───────────────────────────────────────────────────────

    override fun visitCall(expression: IrCall): IrExpression {
        val visited = super.visitCall(expression) as IrCall
        val callee = visited.symbol.owner
        val dispatchType = callee.dispatchReceiverParameter?.type ?: return visited
        if (dispatchType.classFqName != DBSET_FQ) return visited

        return when (callee.name.asString()) {
            "filter"             -> rewriteFilter(visited)
            "map"                -> rewriteMap(visited)
            "sortedBy"           -> rewriteSortedBy(visited, descending = false)
            "sortedByDescending" -> rewriteSortedBy(visited, descending = true)
            "thenBy"             -> rewriteSortedBy(visited, descending = false, exprMethod = "thenByExpr")
            "thenByDescending"   -> rewriteSortedBy(visited, descending = true, exprMethod = "thenByDescendingExpr")
            "any"                -> rewriteFilterStyle(visited, "anyExpr")
            "all"                -> rewriteFilterStyle(visited, "allExpr")
            "sum"                -> rewriteAggregateStyle(visited, "sumExpr")
            "avg"                -> rewriteAggregateStyle(visited, "avgExpr")
            "min"                -> rewriteAggregateStyle(visited, "minExpr")
            "max"                -> rewriteAggregateStyle(visited, "maxExpr")
            else                 -> visited
        }
    }

    // ─── rewrites ────────────────────────────────────────────────────────────

    private fun rewriteFilter(call: IrCall): IrExpression {
        val lambda = extractLambda(call) ?: return call
        val param = lambdaParam(lambda)
        val body = extractBody(lambda) ?: return call
        val predicateIr = try { buildExpr(body, param) }
        catch (e: UnsupportedQueryExpression) { return call }
        val lambdaIr = buildLambdaExprIr(param, predicateIr)
        return irCall(dbSetMethod("filterExpr"), call.type, call.dispatchReceiver!!, lambdaIr)
    }

    private fun rewriteMap(call: IrCall): IrExpression {
        val lambda = extractLambda(call) ?: return call
        val param = lambdaParam(lambda)
        val body = extractBody(lambda) ?: return call
        val selectorIr = try { buildSinglePropExpr(body, param) }
        catch (e: UnsupportedQueryExpression) { return call }
        val lambdaIr = buildLambdaExprIr(param, selectorIr)
        val kClassIr = buildKClassRef(lambda.function.returnType)

        return irCallImpl(call.startOffset, call.endOffset, call.type, dbSetMethod("mapExpr")).also {
            it.arguments.add(call.dispatchReceiver!!)
            @Suppress("UNCHECKED_CAST")
            (it.typeArguments as MutableList<IrType?>).add(lambda.function.returnType)
            it.arguments.add(kClassIr)
            it.arguments.add(lambdaIr)
        }
    }

    private fun rewriteSortedBy(
        call: IrCall,
        descending: Boolean,
        exprMethod: String = if (descending) "sortedByDescendingExpr" else "sortedByExpr"
    ): IrExpression {
        val lambda = extractLambda(call) ?: return call
        val param = lambdaParam(lambda)
        val body = extractBody(lambda) ?: return call
        val keyIr = try { buildSinglePropExpr(body, param) }
        catch (e: UnsupportedQueryExpression) { return call }
        val lambdaIr = buildLambdaExprIr(param, keyIr)
        return irCall(dbSetMethod(exprMethod), call.type, call.dispatchReceiver!!, lambdaIr)
    }

    // any/all: same as filter but returns Boolean (not DbSet)
    private fun rewriteFilterStyle(call: IrCall, exprMethod: String): IrExpression {
        val lambda = extractLambda(call) ?: return call
        val param = lambdaParam(lambda)
        val body = extractBody(lambda) ?: return call
        val predicateIr = try { buildExpr(body, param) }
        catch (e: UnsupportedQueryExpression) { return call }
        val lambdaIr = buildLambdaExprIr(param, predicateIr)
        return irCall(dbSetMethod(exprMethod), call.type, call.dispatchReceiver!!, lambdaIr)
    }

    // sum/avg/min/max: pass KClass + lambda to *Expr
    private fun rewriteAggregateStyle(call: IrCall, exprMethod: String): IrExpression {
        val lambda = extractLambda(call) ?: return call
        val param = lambdaParam(lambda)
        val body = extractBody(lambda) ?: return call
        val selectorIr = try { buildSinglePropExpr(body, param) }
        catch (e: UnsupportedQueryExpression) { return call }
        val lambdaIr = buildLambdaExprIr(param, selectorIr)
        val kClassIr = buildKClassRef(lambda.function.returnType)

        return irCallImpl(call.startOffset, call.endOffset, call.type, dbSetMethod(exprMethod)).also {
            it.arguments.add(call.dispatchReceiver!!)
            @Suppress("UNCHECKED_CAST")
            (it.typeArguments as MutableList<IrType?>).add(lambda.function.returnType)
            it.arguments.add(kClassIr)
            it.arguments.add(lambdaIr)
        }
    }

    // ─── expression tree builder ──────────────────────────────────────────────

    private fun buildExpr(ir: IrExpression, param: IrValueParameter): IrExpression = when {
        ir is IrCall && ir.isPropertyGetterOn(param) -> buildPropertyRef(ir)
        ir is IrConst                             -> buildConstantCall(ir)
        ir is IrGetValue && ir.symbol != param.symbol -> buildCapturedConstant(ir)
        ir is IrCall && isStringMethodCall(ir, param) -> buildStringMethodIr(ir, param)
        ir is IrCall                                  -> buildBinaryOrBool(ir, param)
        ir is IrWhen                                  -> buildFromWhen(ir, param)
        else -> throw UnsupportedQueryExpression("Unsupported expression: ${ir.render()}")
    }

    private fun buildSinglePropExpr(ir: IrExpression, param: IrValueParameter): IrExpression {
        if (ir is IrCall && ir.isPropertyGetterOn(param)) return buildPropertyRef(ir)
        throw UnsupportedQueryExpression("map/sortedBy selector must be a single property access (it.prop)")
    }

    private fun buildBinaryOrBool(call: IrCall, param: IrValueParameter): IrExpression {
        val origin = call.origin
            ?: throw UnsupportedQueryExpression("Cannot translate call without origin: ${call.render()}")
        return when (origin) {
            IrStatementOrigin.GT     -> buildBinary(fnGt, call, param)
            IrStatementOrigin.GTEQ   -> buildBinary(fnGe, call, param)
            IrStatementOrigin.LT     -> buildBinary(fnLt, call, param)
            IrStatementOrigin.LTEQ   -> buildBinary(fnLe, call, param)
            IrStatementOrigin.EQEQ   -> {
                val (rawLhs, rawRhs) = rawBinaryArgs(call)
                when {
                    rawRhs is IrConst && rawRhs.value == null ->
                        buildNullCheckIr(buildExpr(rawLhs, param), isNotNull = false)
                    rawLhs is IrConst && rawLhs.value == null ->
                        buildNullCheckIr(buildExpr(rawRhs, param), isNotNull = false)
                    else -> buildBinary(fnEq, call, param)
                }
            }
            IrStatementOrigin.EXCLEQ -> {
                val (rawLhs, rawRhs) = rawBinaryArgs(call)
                when {
                    rawRhs is IrConst && rawRhs.value == null ->
                        buildNullCheckIr(buildExpr(rawLhs, param), isNotNull = true)
                    rawLhs is IrConst && rawLhs.value == null ->
                        buildNullCheckIr(buildExpr(rawRhs, param), isNotNull = true)
                    else -> buildBinary(fnNe, call, param)
                }
            }
            IrStatementOrigin.ANDAND -> buildBinary(fnAnd, call, param)
            IrStatementOrigin.OROR   -> buildBinary(fnOr, call, param)
            IrStatementOrigin.EXCL   -> buildUnary(call, param)
            else -> throw UnsupportedQueryExpression("Unsupported operator origin $origin")
        }
    }

    private fun rawBinaryArgs(call: IrCall): Pair<IrExpression, IrExpression> {
        val recv = call.dispatchReceiver ?: call.extensionReceiver
        return if (recv != null) {
            recv to call.getValueArgument(0)!!
        } else {
            call.getValueArgument(0)!! to call.getValueArgument(1)!!
        }
    }

    private fun buildBinary(fn: IrSimpleFunctionSymbol, call: IrCall, param: IrValueParameter): IrExpression {
        val recv = call.dispatchReceiver ?: call.extensionReceiver
        val left: IrExpression
        val right: IrExpression
        if (recv != null) {
            left = buildExpr(recv, param)
            right = buildExpr(call.getValueArgument(0)!!, param)
        } else {
            left = buildExpr(call.getValueArgument(0)!!, param)
            right = buildExpr(call.getValueArgument(1)!!, param)
        }
        return irBuilderCall(fn, left, right)
    }

    private fun buildUnary(call: IrCall, param: IrValueParameter): IrExpression {
        val operand = buildExpr(call.dispatchReceiver ?: call.getValueArgument(0)!!, param)
        return irCallImpl(call.startOffset, call.endOffset, expressionType, fnNot).also {
            it.arguments.add(operand)
        }
    }

    private fun isStringMethodCall(call: IrCall, param: IrValueParameter): Boolean {
        val name = call.symbol.owner.name.asString()
        if (name !in listOf("startsWith", "endsWith", "contains")) return false
        val recv = call.dispatchReceiver ?: call.extensionReceiver ?: return false
        return recv is IrCall && recv.isPropertyGetterOn(param)
    }

    private fun buildStringMethodIr(call: IrCall, param: IrValueParameter): IrExpression {
        val name = call.symbol.owner.name.asString()
        val builderFn = when (name) {
            "startsWith" -> fnStringStartsWith
            "endsWith"   -> fnStringEndsWith
            "contains"   -> fnStringContains
            else -> throw UnsupportedQueryExpression("Unknown string method: $name")
        }
        val recv = (call.dispatchReceiver ?: call.extensionReceiver) as IrCall
        val propIr = buildPropertyRef(recv)
        val rawArg = call.getValueArgument(0)
            ?: throw UnsupportedQueryExpression("String predicate has no argument")
        if (rawArg !is IrConst) throw UnsupportedQueryExpression("String predicate requires a literal argument")
        val argIr = buildConstantCall(rawArg)
        return irBuilderCall(builderFn, propIr, argIr)
    }

    private fun buildNullCheckIr(propIr: IrExpression, isNotNull: Boolean): IrExpression {
        val fn = if (isNotNull) fnIsNotNullPred else fnIsNullPred
        return irCallImpl(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, expressionType, fn).also {
            it.arguments.add(propIr)
        }
    }

    private fun buildFromWhen(when_: IrWhen, param: IrValueParameter): IrExpression {
        val branches = when_.branches
        if (branches.size == 2 && branches[1] is IrElseBranch) {
            val condition = branches[0].condition
            val thenResult = branches[0].result
            val elseResult = (branches[1] as IrElseBranch).result
            return when {
                isConstBool(elseResult, false) -> irBuilderCall(fnAnd, buildExpr(condition, param), buildExpr(thenResult, param))
                isConstBool(thenResult, true)  -> irBuilderCall(fnOr, buildExpr(condition, param), buildExpr(elseResult, param))
                else -> throw UnsupportedQueryExpression("Unsupported IrWhen pattern in query lambda")
            }
        }
        throw UnsupportedQueryExpression("Unsupported IrWhen in query lambda")
    }

    private fun isConstBool(ir: IrExpression, value: Boolean) =
        ir is IrConst && ir.kind == IrConstKind.Boolean && ir.value == value

    // ─── IR builder helpers ───────────────────────────────────────────────────

    private fun buildPropertyRef(getterCall: IrCall): IrExpression {
        val getter = getterCall.symbol.owner
        val propSym = getter.correspondingPropertySymbol
            ?: throw UnsupportedQueryExpression("Cannot resolve property for getter ${getter.name}")
        val receiverType = getterCall.dispatchReceiver!!.type
        val propType = getterCall.type
        val kProp1Type = ctx.irBuiltIns.kProperty1Class.typeWith(receiverType, propType)

        val propRef = IrPropertyReferenceImpl(
            null, getterCall.startOffset, getterCall.endOffset,
            kProp1Type, null, propSym,
            field = null, getter = getter.symbol, setter = null
        ).also {
            @Suppress("UNCHECKED_CAST")
            (it.arguments as ArrayList<IrExpression?>).add(null)
        }

        return irCallImpl(getterCall.startOffset, getterCall.endOffset, expressionType, fnProperty).also {
            it.arguments.add(propRef)
        }
    }

    private fun buildConstantCall(const: IrConst): IrExpression =
        irCallImpl(const.startOffset, const.endOffset, expressionType, fnConstant).also {
            it.arguments.add(const)
        }

    private fun buildCapturedConstant(getValue: IrGetValue): IrExpression =
        irCallImpl(getValue.startOffset, getValue.endOffset, expressionType, fnConstant).also {
            it.arguments.add(getValue)
        }

    private fun buildLambdaExprIr(param: IrValueParameter, bodyExpr: IrExpression): IrExpression {
        val nameConst = IrConstImpl.string(param.startOffset, param.endOffset, ctx.irBuiltIns.stringType, param.name.asString())
        val kClassRef = buildKClassRef(param.type)

        val paramExprType = cls(FqName("dev.efkore.expressions.ParameterExpression")).defaultType
        val paramExprIr = irCallImpl(param.startOffset, param.endOffset, paramExprType, fnParamExpr).also {
            it.arguments.add(nameConst)
            it.arguments.add(kClassRef)
        }

        val lambdaExprType = cls(FqName("dev.efkore.expressions.LambdaExpression")).defaultType
        return irCallImpl(param.startOffset, param.endOffset, lambdaExprType, fnLambdaExpr).also {
            it.arguments.add(paramExprIr)
            it.arguments.add(bodyExpr)
        }
    }

    private fun buildKClassRef(type: IrType): IrExpression {
        val classSymbol = type.classOrNull
            ?: throw UnsupportedQueryExpression("Cannot get KClass for type $type")
        return IrClassReferenceImpl(
            null, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
            ctx.irBuiltIns.kClassClass.typeWith(type),
            classSymbol, type
        )
    }

    private fun irCall(sym: IrSimpleFunctionSymbol, type: IrType, dispatch: IrExpression, vararg args: IrExpression): IrExpression =
        irCallImpl(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, type, sym).also { call ->
            call.arguments.add(dispatch)
            args.forEach { call.arguments.add(it) }
        }

    private fun irBuilderCall(sym: IrSimpleFunctionSymbol, vararg args: IrExpression): IrExpression =
        irCallImpl(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, expressionType, sym).also { call ->
            args.forEach { call.arguments.add(it) }
        }

    // ─── utility ─────────────────────────────────────────────────────────────

    private fun lambdaParam(lambda: IrFunctionExpression): IrValueParameter =
        lambda.function.parameters.first { it.kind == IrParameterKind.Regular }

    private fun extractLambda(call: IrCall): IrFunctionExpression? =
        call.getValueArgument(0) as? IrFunctionExpression

    private fun extractBody(lambda: IrFunctionExpression): IrExpression? =
        when (val body = lambda.function.body) {
            is IrBlockBody -> (body.statements.singleOrNull() as? IrReturn)?.value
            is IrExpressionBody -> body.expression
            else -> null
        }

    private fun IrCall.isPropertyGetterOn(param: IrValueParameter): Boolean {
        val getter = symbol.owner
        if (getter.correspondingPropertySymbol == null) return false
        val name = getter.name.asString()
        if (!name.startsWith("<get-")) return false
        val recv = dispatchReceiver as? IrGetValue ?: return false
        return recv.symbol == param.symbol
    }
}
