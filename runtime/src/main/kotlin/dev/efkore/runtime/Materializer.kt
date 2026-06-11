package dev.efkore.runtime

import dev.efkore.metadata.EntityModel
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.primaryConstructor

object Materializer {
    fun <T : Any> materialize(row: Row, meta: RowMetadata, type: KClass<T>, model: EntityModel<*>): T {
        // If only one column is selected (scalar projection), return that directly.
        val colNames = meta.columnMetadatas.map { it.name.lowercase() }
        if (colNames.size == 1 && type != model.entityClass) {
            @Suppress("UNCHECKED_CAST")
            return row.get(0, type.javaObjectType) as T
        }

        // Full entity: try primary constructor match first (only if it takes parameters).
        val ctor = type.primaryConstructor
        if (ctor != null && ctor.parameters.isNotEmpty()) {
            val args = ctor.parameters.associateWith { param ->
                val colName = model.columns.find {
                    it.property.name == param.name
                }?.columnName ?: param.name!!.lowercase()
                row.get(colName, param.type.classifier.let { (it as KClass<*>).javaObjectType })
            }
            return ctor.callBy(args)
        }

        // Fallback: no-arg constructor + set properties.
        val instance = type.java.getDeclaredConstructor().newInstance()
        model.columns.forEach { col ->
            @Suppress("UNCHECKED_CAST")
            val prop = col.property as KMutableProperty1<T, Any?>
            val javaType = prop.returnType.classifier.let { (it as KClass<*>).javaObjectType }
            prop.set(instance, row.get(col.columnName, javaType))
        }
        return instance
    }
}
