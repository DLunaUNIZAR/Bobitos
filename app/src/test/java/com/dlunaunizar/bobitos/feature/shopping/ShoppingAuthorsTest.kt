package com.dlunaunizar.bobitos.feature.shopping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingAuthorsTest {
    @Test
    fun aSingleMemberSpaceHidesAuthors() = assertFalse(showsAuthor(1))

    @Test
    fun aSharedSpaceShowsAuthors() = assertTrue(showsAuthor(2))

    @Test
    fun withoutTheCountAuthorsAreShown() = assertTrue(showsAuthor(null))
}
