package dev.efkore.sample

import dev.efkore.DbContext
import dev.efkore.DbSet
import io.r2dbc.spi.ConnectionFactory

class BloggingContext(connectionFactory: ConnectionFactory) : DbContext(connectionFactory) {
    val blogs: DbSet<Blog> by lazy { set() }
}
