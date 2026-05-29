package dev.efkore.runtime

import dev.efkore.metadata.EntityModel
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

class Materializer<T : Any>(
    private val entityType: KClass<T>,
    private val model: EntityModel<*>
) {
    fun materialize(row: Map<String, Any?>): T {
        val instance = entityType.java.getDeclaredConstructor().newInstance()
        model.columns.forEach { col ->
            @Suppress("UNCHECKED_CAST")
            val prop = col.property as KMutableProperty1<T, Any?>
            val value = coerce(row[col.columnName.lowercase()], prop.returnType.classifier as KClass<*>)
            prop.set(instance, value)
        }
        return instance
    }

    private fun coerce(value: Any?, target: KClass<*>): Any? {
        if (value == null) return null
        return when (target) {
            Int::class -> (value as Number).toInt()
            Long::class -> (value as Number).toLong()
            String::class -> value.toString()
            Boolean::class -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value.toString().toBoolean()
            }
            Double::class -> (value as Number).toDouble()
            Float::class -> (value as Number).toFloat()
            else -> value
        }
    }
}
