package dev.efkore.sample

import dev.efkore.DbContext
import dev.efkore.DbContextOptions
import dev.efkore.DbSet

class BloggingContext(opts: DbContextOptions) : DbContext(opts) {
    val blogs: DbSet<Blog> = dbSet()
}
