package dev.efkore.sample

import dev.efkore.expressions.constant
import dev.efkore.expressions.eq
import dev.efkore.expressions.lambdaExpr
import dev.efkore.expressions.paramExpr
import dev.efkore.expressions.property
import dev.efkore.metadata.EntityModel
import dev.efkore.runtime.R2dbcExecutor
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QuerySliceTest {

    private val url = "r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1"

    @Test
    fun `filter map sortedBy skip take`() = runBlocking {
        val connectionFactory = ConnectionFactories.get(url)
        val ctx = BloggingContext(connectionFactory)

        val ddl = EntityModel.resolve<Blog>().createDdl()
        val executor = R2dbcExecutor(connectionFactory)
        executor.executeUpdate(ddl, emptyList())

        ctx.blogs.add(Blog(0, "Alpha", "Content A", true))
        ctx.blogs.add(Blog(0, "Beta", "Content B", false))
        ctx.blogs.add(Blog(0, "Gamma", "Content C", true))
        ctx.blogs.add(Blog(0, "Delta", "Content D", true))
        ctx.saveChanges()

        val param = paramExpr("it", Blog::class)
        val results = ctx.blogs
            .filterExpr(lambdaExpr(param, eq(property(Blog::published), constant(true))))
            .mapExpr(Blog::class, lambdaExpr(param, property(Blog::title)))
            .sortedByExpr(lambdaExpr(param, property(Blog::title)))
            .skip(1)
            .take(2)
            .toList()

        assertEquals(2, results.size)
        assertEquals("Delta", results[0].title)
        assertEquals("Gamma", results[1].title)
    }
}
