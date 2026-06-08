package dev.efkore.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndToEndTest {

    @Test
    fun `basic filter compiles with plugin`() {
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("FilterTest.kt", """
                    import dev.efkore.DbSet
                    import dev.efkore.DbContext
                    import io.r2dbc.spi.ConnectionFactory
                    import io.r2dbc.spi.Connection
                    import org.reactivestreams.Publisher

                    data class User(val name: String, val age: Int)

                    fun testFilter() {
                        val connFactory = object : ConnectionFactory {
                            override fun create(): Publisher<out Connection> = throw NotImplementedError()
                            override fun getMetadata() = throw NotImplementedError()
                        }
                        val ctx = object : DbContext(connFactory) {
                            val users = set<User>()
                        }
                        ctx.users.filter { it.age > 18 }
                    }
                """)
            )
            compilerPlugins = listOf(EfkoreCompilerRegistrar())
            inheritClassPath = true
        }.compile()

        assertEquals(ExitCode.OK, result.exitCode, "filter compilation failed: ${result.messages}")
    }

    @Test
    fun `basic map compiles with plugin`() {
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("MapTest.kt", """
                    import dev.efkore.DbSet
                    import dev.efkore.DbContext
                    import io.r2dbc.spi.ConnectionFactory
                    import io.r2dbc.spi.Connection
                    import org.reactivestreams.Publisher

                    data class User(val name: String, val age: Int)

                    fun testMap() {
                        val connFactory = object : ConnectionFactory {
                            override fun create(): Publisher<out Connection> = throw NotImplementedError()
                            override fun getMetadata() = throw NotImplementedError()
                        }
                        val ctx = object : DbContext(connFactory) {
                            val users = set<User>()
                        }
                        ctx.users.map { it.name }
                    }
                """)
            )
            compilerPlugins = listOf(EfkoreCompilerRegistrar())
            inheritClassPath = true
        }.compile()

        assertEquals(ExitCode.OK, result.exitCode, "map compilation failed: ${result.messages}")
    }

    @Test
    fun `basic sortedBy compiles with plugin`() {
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("SortedByTest.kt", """
                    import dev.efkore.DbSet
                    import dev.efkore.DbContext
                    import io.r2dbc.spi.ConnectionFactory
                    import io.r2dbc.spi.Connection
                    import org.reactivestreams.Publisher

                    data class User(val name: String, val age: Int)

                    fun testSortedBy() {
                        val connFactory = object : ConnectionFactory {
                            override fun create(): Publisher<out Connection> = throw NotImplementedError()
                            override fun getMetadata() = throw NotImplementedError()
                        }
                        val ctx = object : DbContext(connFactory) {
                            val users = set<User>()
                        }
                        ctx.users.sortedBy { it.name }
                    }
                """)
            )
            compilerPlugins = listOf(EfkoreCompilerRegistrar())
            inheritClassPath = true
        }.compile()

        assertEquals(ExitCode.OK, result.exitCode, "sortedBy compilation failed: ${result.messages}")
    }

    @Test
    fun `chained filter map sortedBy compiles with plugin`() {
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("ChainedTest.kt", """
                    import dev.efkore.DbSet
                    import dev.efkore.DbContext
                    import io.r2dbc.spi.ConnectionFactory
                    import io.r2dbc.spi.Connection
                    import org.reactivestreams.Publisher

                    data class User(val name: String, val age: Int)

                    fun testChained() {
                        val connFactory = object : ConnectionFactory {
                            override fun create(): Publisher<out Connection> = throw NotImplementedError()
                            override fun getMetadata() = throw NotImplementedError()
                        }
                        val ctx = object : DbContext(connFactory) {
                            val users = set<User>()
                        }
                        ctx.users.filter { it.age > 18 }.map { it.name }.sortedBy { it }
                    }
                """)
            )
            compilerPlugins = listOf(EfkoreCompilerRegistrar())
            inheritClassPath = true
        }.compile()

        assertEquals(ExitCode.OK, result.exitCode, "chained compilation failed: ${result.messages}")
    }

    @Test
    fun `expression tree classes are accessible at compile time`() {
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("ExpressionAccessTest.kt", """
                    import dev.efkore.expressions.*
                    import kotlin.reflect.KProperty1

                    data class Item(val title: String, val price: Double)

                    fun testExpressions() {
                        val prop: KProperty1<Item, *> = Item::title
                        val propExpr = property(prop)
                        val constExpr = constant(42)
                        val gtExpr = gt(propExpr, constExpr)
                        val lambda = lambdaExpr(paramExpr("x", Item::class), gtExpr)
                        val _ = and(propExpr, constExpr)
                        val _ = or(propExpr, constExpr)
                        val _ = not(propExpr)
                        val _ = eq(propExpr, constExpr)
                        val _ = ne(propExpr, constExpr)
                        val _ = ge(propExpr, constExpr)
                        val _ = le(propExpr, constExpr)
                        val _ = lt(propExpr, constExpr)
                        val _ = stringStartsWith(propExpr, ConstantExpression("hello"))
                        val _ = stringEndsWith(propExpr, ConstantExpression("world"))
                        val _ = stringContains(propExpr, ConstantExpression("test"))
                        val _ = isNullPred(propExpr)
                        val _ = isNotNullPred(propExpr)
                    }
                """)
            )
            inheritClassPath = true
        }.compile()

        assertTrue(result.exitCode == ExitCode.OK, "expression access compilation failed: ${result.messages}")
    }
}
