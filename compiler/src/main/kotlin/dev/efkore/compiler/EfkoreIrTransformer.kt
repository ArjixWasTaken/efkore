package dev.efkore.compiler

import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class EfkoreIrTransformer : IrElementTransformerVoid() {

    override fun visitModuleFragment(module: IrModuleFragment): IrModuleFragment {
        return super.visitModuleFragment(module)
    }
}
