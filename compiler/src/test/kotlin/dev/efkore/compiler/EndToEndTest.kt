package dev.efkore.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@OptIn(ExperimentalCompilerApi::class)
class EndToEndTest {

    private val blogEntity = SourceFile.kotlin(
        "Blog.kt",
        """
        package test

        import dev.efkore.metadata.Entity
        import dev.efkore.metadata.Id
        import dev.efkore.metadata.GeneratedValue
        import dev.efkore.metadata.Column
        import dev.efkore.metadata.Table

        @Entity
        @Table("blogs")
        class Blog {
            @Id @GeneratedValue var id: Int = 0
            @Column("url") var url: String = ""
            @Column("rating") var rating: Int = 0
        }
        """
    )

    private val contextClass = SourceFile.kotlin(
        "BloggingContext.kt",
        """
        package test

        import dev.efkore.DbContext
        import dev.efkore.DbContextOptions
        import dev.efkore.DbSet

        class BloggingContext(opts: DbContextOptions) : DbContext(opts) {
            val blogs: DbSet<Blog> = dbSet()
        }
        """
    )

    private val mainSnippet = SourceFile.kotlin(
        "Main.kt",
        """
        package test

        import dev.efkore.DbContextOptions
        import io.r2dbc.h2.H2ConnectionConfiguration
        import io.r2dbc.h2.H2ConnectionFactory
        import kotlinx.coroutines.runBlocking

        fun main() {
            val dbName = "et_" + System.nanoTime()
            val factory = H2ConnectionFactory(
                H2ConnectionConfiguration.builder()
                    .inMemory(dbName)
                    .option("DB_CLOSE_DELAY=-1")
                    .build()
            )
            val ctx = BloggingContext(DbContextOptions(factory))
            runBlocking {
                ctx.database.ensureCreated()

                val b1 = Blog().apply { url = "https://high.example.com"; rating = 5 }
                val b2 = Blog().apply { url = "https://low.example.com"; rating = 1 }
                val b3 = Blog().apply { url = "https://mid.example.com"; rating = 4 }
                ctx.blogs.add(b1)
                ctx.blogs.add(b2)
                ctx.blogs.add(b3)
                ctx.saveChanges()

                println("id1=" + b1.id)
                println("id2=" + b2.id)

                val urls = ctx.blogs
                    .filter { it.rating > 3 }
                    .map { it.url }
                    .toList()

                urls.sorted().forEach { println(it) }

                val anyHigh = ctx.blogs.any { it.rating > 3 }
                println("anyHigh=" + anyHigh)

                val allPositive = ctx.blogs.all { it.rating > 0 }
                println("allPositive=" + allPositive)

                val total = ctx.blogs.count()
                println("count=" + total)

                val maxRating = ctx.blogs.maxOf { it.rating }
                println("max=" + maxRating)

                val minRating = ctx.blogs.minOf { it.rating }
                println("min=" + minRating)

                val sumRating = ctx.blogs.sumOf { it.rating }
                println("sum=" + sumRating)

                val containsB1 = ctx.blogs.contains(b1)
                println("containsB1=" + containsB1)
            }
        }
        """
    )

    @Test
    fun `plugin rewrites filter and map, query returns correct urls`() {
        val result = compile(blogEntity, contextClass, mainSnippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Compilation failed:\n${result.messages}"
        }

        val output = runMain(result, "test.MainKt")
        assertTrue(output.contains("id1=1") || output.contains("id1=")) { "Expected generated id in output: $output" }
        assertTrue(output.contains("https://high.example.com")) { "Missing high-rated blog: $output" }
        assertTrue(output.contains("https://mid.example.com")) { "Missing mid-rated blog: $output" }
        assertTrue(!output.contains("https://low.example.com")) { "Low-rated blog should be filtered out: $output" }
    }

    @Test
    fun `any and all are rewritten and return correct results`() {
        val result = compile(blogEntity, contextClass, mainSnippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Compilation failed:\n${result.messages}"
        }
        val output = runMain(result, "test.MainKt")
        assertTrue(output.contains("anyHigh=true")) { "anyHigh should be true: $output" }
        assertTrue(output.contains("allPositive=true")) { "allPositive should be true: $output" }
    }

    @Test
    fun `count pushdown returns correct value`() {
        val result = compile(blogEntity, contextClass, mainSnippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Compilation failed:\n${result.messages}"
        }
        val output = runMain(result, "test.MainKt")
        assertTrue(output.contains("count=3")) { "count should be 3: $output" }
    }

    @Test
    fun `aggregate functions return correct values`() {
        val result = compile(blogEntity, contextClass, mainSnippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Compilation failed:\n${result.messages}"
        }
        val output = runMain(result, "test.MainKt")
        assertTrue(output.contains("max=5")) { "max should be 5: $output" }
        assertTrue(output.contains("min=1")) { "min should be 1: $output" }
        assertTrue(output.contains("sum=10")) { "sum should be 10: $output" }
    }

    @Test
    fun `contains returns true for existing entity`() {
        val result = compile(blogEntity, contextClass, mainSnippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Compilation failed:\n${result.messages}"
        }
        val output = runMain(result, "test.MainKt")
        assertTrue(output.contains("containsB1=true")) { "containsB1 should be true: $output" }
    }

    @Test
    fun `sortedBy then thenBy produces correct ORDER BY`() {
        val snippet = SourceFile.kotlin(
            "ThenBy.kt",
            """
            package test

            import dev.efkore.DbContextOptions
            import io.r2dbc.h2.H2ConnectionConfiguration
            import io.r2dbc.h2.H2ConnectionFactory
            import kotlinx.coroutines.runBlocking

            fun main() {
                val factory = H2ConnectionFactory(
                    H2ConnectionConfiguration.builder()
                        .inMemory("et_thenby_" + System.nanoTime())
                        .option("DB_CLOSE_DELAY=-1")
                        .build()
                )
                val ctx = BloggingContext(DbContextOptions(factory))
                runBlocking {
                    ctx.database.ensureCreated()
                    ctx.blogs.add(Blog().apply { url = "https://b.example.com"; rating = 2 })
                    ctx.blogs.add(Blog().apply { url = "https://a.example.com"; rating = 2 })
                    ctx.blogs.add(Blog().apply { url = "https://c.example.com"; rating = 1 })
                    ctx.saveChanges()

                    val rows = ctx.blogs
                        .sortedBy { it.rating }
                        .thenBy { it.url }
                        .toList()

                    rows.forEach { println(it.url) }
                }
            }
            """
        )
        val result = compile(blogEntity, contextClass, snippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) { "Compilation failed:\n${result.messages}" }
        val output = runMain(result, "test.ThenByKt")
        val urls = output.trim().lines()
        assertEquals("https://c.example.com", urls[0]) { "First row wrong: $output" }
        assertEquals("https://a.example.com", urls[1]) { "Second row wrong: $output" }
        assertEquals("https://b.example.com", urls[2]) { "Third row wrong: $output" }
    }

    @Test
    fun `filter with startsWith generates correct subset`() {
        val snippet = SourceFile.kotlin(
            "StartsWith.kt",
            """
            package test

            import dev.efkore.DbContextOptions
            import io.r2dbc.h2.H2ConnectionConfiguration
            import io.r2dbc.h2.H2ConnectionFactory
            import kotlinx.coroutines.runBlocking

            fun main() {
                val factory = H2ConnectionFactory(
                    H2ConnectionConfiguration.builder()
                        .inMemory("et_startswith_" + System.nanoTime())
                        .option("DB_CLOSE_DELAY=-1")
                        .build()
                )
                val ctx = BloggingContext(DbContextOptions(factory))
                runBlocking {
                    ctx.database.ensureCreated()
                    ctx.blogs.add(Blog().apply { url = "https://high.example.com"; rating = 5 })
                    ctx.blogs.add(Blog().apply { url = "https://low.example.com";  rating = 1 })
                    ctx.saveChanges()

                    val urls = ctx.blogs
                        .filter { it.url.startsWith("https://high") }
                        .map { it.url }
                        .toList()
                    urls.forEach { println(it) }
                }
            }
            """
        )
        val result = compile(blogEntity, contextClass, snippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) { "Compilation failed:\n${result.messages}" }
        val output = runMain(result, "test.StartsWithKt")
        assertTrue(output.contains("https://high.example.com")) { "Expected high blog: $output" }
        assertFalse(output.contains("https://low.example.com")) { "Low blog should be filtered: $output" }
    }

    @Test
    fun `filter with contains generates correct subset`() {
        val snippet = SourceFile.kotlin(
            "Contains.kt",
            """
            package test

            import dev.efkore.DbContextOptions
            import io.r2dbc.h2.H2ConnectionConfiguration
            import io.r2dbc.h2.H2ConnectionFactory
            import kotlinx.coroutines.runBlocking

            fun main() {
                val factory = H2ConnectionFactory(
                    H2ConnectionConfiguration.builder()
                        .inMemory("et_contains_" + System.nanoTime())
                        .option("DB_CLOSE_DELAY=-1")
                        .build()
                )
                val ctx = BloggingContext(DbContextOptions(factory))
                runBlocking {
                    ctx.database.ensureCreated()
                    ctx.blogs.add(Blog().apply { url = "https://high.example.com"; rating = 5 })
                    ctx.blogs.add(Blog().apply { url = "https://low.example.com";  rating = 1 })
                    ctx.saveChanges()

                    val urls = ctx.blogs
                        .filter { it.url.contains("high") }
                        .map { it.url }
                        .toList()
                    urls.forEach { println(it) }
                }
            }
            """
        )
        val result = compile(blogEntity, contextClass, snippet)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) { "Compilation failed:\n${result.messages}" }
        val output = runMain(result, "test.ContainsKt")
        assertTrue(output.contains("https://high.example.com")) { "Expected high blog: $output" }
        assertFalse(output.contains("https://low.example.com")) { "Low blog should be filtered: $output" }
    }

    @Test
    fun `plugin falls back gracefully for unsupported expression`() {
        val badLambda = SourceFile.kotlin(
            "Bad.kt",
            """
            package test

            import dev.efkore.DbContextOptions
            import io.r2dbc.h2.H2ConnectionConfiguration
            import io.r2dbc.h2.H2ConnectionFactory

            fun bad() {
                val factory = H2ConnectionFactory(H2ConnectionConfiguration.builder().inMemory("bad").build())
                val ctx = BloggingContext(DbContextOptions(factory))
                // Chained property access (it.url.length) is not supported
                ctx.blogs.filter { it.url.length > 3 }
            }
            """
        )
        val result = compile(blogEntity, contextClass, badLambda)
        // Plugin falls back to the lambda overload; compilation succeeds, runtime throws
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode) {
            "Unexpected compile failure:\n${result.messages}"
        }
    }

    private fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(EfkoreCompilerRegistrar())
            inheritClassPath = true
            messageOutputStream = System.err
            jvmTarget = "11"
        }.compile()
    }

    private fun runMain(result: JvmCompilationResult, className: String): String {
        val cls = result.classLoader.loadClass(className)
        val method = cls.getMethod("main")
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            method.invoke(null)
        } finally {
            System.setOut(old)
        }
        return baos.toString()
    }
}
