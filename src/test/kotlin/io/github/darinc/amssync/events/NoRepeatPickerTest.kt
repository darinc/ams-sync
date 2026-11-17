package io.github.darinc.amssync.events

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class NoRepeatPickerTest : DescribeSpec({

    val pool = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

    it("does not repeat a line within half the pool size") {
        val picker = NoRepeatPicker(random = Random(42))
        val picks = List(500) { picker.pick("pool", pool) }
        picks.windowed(pool.size / 2 + 1).forEach { window ->
            window.toSet().size shouldBe window.size
        }
    }

    it("caps memory so large pools still cycle") {
        val big = (1..40).map { "line $it" }
        val picker = NoRepeatPicker(maxMemory = 3, random = Random(7))
        val picks = List(200) { picker.pick("big", big) }
        picks.windowed(4).forEach { window -> window.toSet().size shouldBe 4 }
    }

    it("keeps separate history per pool") {
        val picker = NoRepeatPicker(random = Random(1))
        val first = picker.pick("one", listOf("x", "y"))
        // A different pool is unaffected by "one"'s history
        List(20) { picker.pick("two", listOf("x")) }.toSet() shouldBe setOf("x")
        picker.pick("one", listOf("x", "y")).let { second -> listOf(first) shouldNotContain second }
    }

    it("still answers for a single-line pool") {
        val picker = NoRepeatPicker()
        repeat(3) { picker.pick("solo", listOf("only")) shouldBe "only" }
    }

    it("rejects an empty pool") {
        shouldThrow<IllegalArgumentException> { NoRepeatPicker().pick("empty", emptyList()) }
    }
})
