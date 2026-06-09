package dev.efkore.sample

import dev.efkore.DbContextOptions
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuerySliceTest {

    private fun makeContext(dbName: String): BloggingContext {
        val factory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .inMemory(dbName)
                .option("DB_CLOSE_DELAY=-1")
                .build()
        )
        return BloggingContext(DbContextOptions(factory))
    }

    @Test
    fun `ensureCreated + add + saveChanges + filter query`() = runTest {
        val ctx = makeContext("query_slice_test")
        ctx.database.ensureCreated()

        val b1 = Blog(url = "https://high.example.com", rating = 5)
        val b2 = Blog(url = "https://low.example.com", rating = 1)
        val b3 = Blog(url = "https://mid.example.com", rating = 4)

        ctx.blogs.addAll(listOf(b1, b2, b3))
        val saved = ctx.saveChanges()

        assertEquals(3, saved)
        assertTrue(b1.id > 0) { "Generated key should be set: b1.id=${b1.id}" }
        assertTrue(b2.id > 0) { "Generated key should be set: b2.id=${b2.id}" }

        val urls = ctx.blogs
            .filter { it.rating > 3 }
            .map { it.url }
            .toList()

        assertEquals(2, urls.size) { "Expected 2 high-rated blogs, got $urls" }
        assertTrue(urls.contains("https://high.example.com"))
        assertTrue(urls.contains("https://mid.example.com"))
    }

    @Test
    fun `count pushdown`() = runTest {
        val ctx = makeContext("count_test")
        ctx.database.ensureCreated()

        repeat(3) { i ->
            ctx.blogs.add(Blog().apply { url = "https://blog$i.example.com"; rating = i + 1 })
        }
        ctx.saveChanges()

        assertEquals(3L, ctx.blogs.count())
    }

    @Test
    fun `any and all`() = runTest {
        val ctx = makeContext("any_all_test")
        ctx.database.ensureCreated()

        val b1 = Blog().apply { url = "https://high.example.com"; rating = 5 }
        val b2 = Blog().apply { url = "https://low.example.com"; rating = 1 }
        val b3 = Blog().apply { url = "https://mid.example.com"; rating = 4 }
        ctx.blogs.addAll(listOf(b1, b2, b3))
        ctx.saveChanges()

        assertTrue(ctx.blogs.any { it.rating > 3 })
        assertTrue(ctx.blogs.all { it.rating > 0 })
        assertFalse(ctx.blogs.all { it.rating > 3 })
    }

    @Test
    fun `aggregate functions`() = runTest {
        val ctx = makeContext("agg_test")
        ctx.database.ensureCreated()

        val b1 = Blog().apply { url = "https://high.example.com"; rating = 5 }
        val b2 = Blog().apply { url = "https://low.example.com"; rating = 1 }
        val b3 = Blog().apply { url = "https://mid.example.com"; rating = 4 }
        ctx.blogs.addAll(listOf(b1, b2, b3))
        ctx.saveChanges()

        assertEquals(10, ctx.blogs.sumOf { it.rating })
        assertEquals(5, ctx.blogs.maxOf { it.rating })
        assertEquals(1, ctx.blogs.minOf { it.rating })
    }

    @Test
    fun `distinct and single`() = runTest {
        val ctx = makeContext("distinct_test")
        ctx.database.ensureCreated()

        ctx.blogs.add(Blog().apply { url = "https://only.example.com"; rating = 3 })
        ctx.saveChanges()

        val all = ctx.blogs.distinct().toList()
        assertEquals(1, all.size)

        val one = ctx.blogs.single()
        assertEquals("https://only.example.com", one.url)

        val oneOrNull = ctx.blogs.singleOrNull()
        assertEquals("https://only.example.com", oneOrNull?.url)
    }

    @Test
    fun `contains`() = runTest {
        val ctx = makeContext("contains_test")
        ctx.database.ensureCreated()

        val b1 = Blog().apply { url = "https://high.example.com"; rating = 5 }
        ctx.blogs.add(b1)
        ctx.saveChanges()

        assertTrue(ctx.blogs.contains(b1))
        assertFalse(ctx.blogs.contains(Blog()))
    }

    @Test
    fun `sortedBy and thenBy produce correct ordering`() = runTest {
        val ctx = makeContext("thenby_test")
        ctx.database.ensureCreated()

        ctx.blogs.add(Blog().apply { url = "https://b.example.com"; rating = 2 })
        ctx.blogs.add(Blog().apply { url = "https://a.example.com"; rating = 2 })
        ctx.blogs.add(Blog().apply { url = "https://c.example.com"; rating = 1 })
        ctx.saveChanges()

        val rows = ctx.blogs.sortedBy { it.rating }.thenBy { it.url }.toList()

        assertEquals("https://c.example.com", rows[0].url)
        assertEquals("https://a.example.com", rows[1].url)
        assertEquals("https://b.example.com", rows[2].url)
    }

    @Test
    fun `filter with startsWith returns correct subset`() = runTest {
        val ctx = makeContext("startswith_test")
        ctx.database.ensureCreated()

        ctx.blogs.add(Blog().apply { url = "https://high.example.com"; rating = 5 })
        ctx.blogs.add(Blog().apply { url = "https://low.example.com";  rating = 1 })
        ctx.saveChanges()

        val urls = ctx.blogs.filter { it.url.startsWith("https://high") }.map { it.url }.toList()
        assertEquals(1, urls.size)
        assertEquals("https://high.example.com", urls[0])
    }

    @Test
    fun `filter with contains returns correct subset`() = runTest {
        val ctx = makeContext("containsstr_test")
        ctx.database.ensureCreated()

        ctx.blogs.add(Blog().apply { url = "https://high.example.com"; rating = 5 })
        ctx.blogs.add(Blog().apply { url = "https://low.example.com";  rating = 1 })
        ctx.saveChanges()

        val urls = ctx.blogs.filter { it.url.contains("high") }.map { it.url }.toList()
        assertEquals(1, urls.size)
        assertEquals("https://high.example.com", urls[0])
    }

    @Test
    fun `find returns entity by pk, null for missing`() = runTest {
        val ctx = makeContext("find_test")
        ctx.database.ensureCreated()

        val b1 = Blog().apply { url = "https://high.example.com"; rating = 5 }
        ctx.blogs.add(b1)
        ctx.saveChanges()

        val found = ctx.blogs.find(b1.id)
        assertNotNull(found)
        assertEquals(b1.id, found!!.id)

        val notFound = ctx.blogs.find(-1)
        assertNull(notFound)
    }

    @Test
    fun `update marks entity modified and persists change`() = runTest {
        val ctx = makeContext("update_test")
        ctx.database.ensureCreated()

        val b1 = Blog().apply { url = "https://old.example.com"; rating = 1 }
        ctx.blogs.add(b1)
        ctx.saveChanges()

        b1.url = "https://new.example.com"
        ctx.blogs.update(b1)
        ctx.saveChanges()

        val reloaded = ctx.blogs.find(b1.id)
        assertNotNull(reloaded)
        assertEquals("https://new.example.com", reloaded!!.url)
    }

    @Test
    fun `transaction happy path completes successfully`() = runTest {
        val ctx = makeContext("txn_happy_test")
        ctx.database.ensureCreated()

        val result = ctx.transaction {
            ctx.blogs.add(Blog().apply { url = "https://txn.example.com"; rating = 3 })
            ctx.saveChanges()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1L, ctx.blogs.count())
    }

    @Test
    fun `transaction propagates exception from block`() = runTest {
        val ctx = makeContext("txn_fail_test")
        ctx.database.ensureCreated()

        var threw = false
        try {
            ctx.transaction<Unit> {
                throw RuntimeException("intentional failure")
            }
        } catch (e: RuntimeException) {
            threw = true
            assertEquals("intentional failure", e.message)
        }
        assertTrue(threw)
    }
}
