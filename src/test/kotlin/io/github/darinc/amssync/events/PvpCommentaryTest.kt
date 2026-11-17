package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class PvpCommentaryTest : DescribeSpec({

    fun generate(context: PvpKillContext, weapon: String? = null) =
        List(50) {
            PvpCommentary.generate("Killer", "Victim", context, weapon, fallback = { "FALLBACK" })
        }

    val plain = PvpKillContext(killerStreak = 1, isRevenge = false, endedStreak = 0)

    it("uses the fallback when nothing notable happened") {
        generate(plain).forEach { it shouldBe "FALLBACK" }
    }

    it("calls out revenge first") {
        val lines = generate(plain.copy(isRevenge = true, killerStreak = 5, endedStreak = 4), "[Fork]")
        lines.forEach {
            it shouldNotContain "FALLBACK"
            it shouldNotContain "kill"
            it shouldNotContain "[Fork]"
        }
    }

    it("calls out an ended streak") {
        generate(plain.copy(endedStreak = 4)).forEach { it shouldContain "4" }
    }

    it("ignores short ended streaks") {
        generate(plain.copy(endedStreak = 2)).forEach { it shouldBe "FALLBACK" }
    }

    it("calls out the killer's streak") {
        generate(plain.copy(killerStreak = 3)).forEach {
            it shouldContain "3"
            it shouldContain "Killer"
        }
    }

    it("mentions a named weapon") {
        generate(plain, "[Fork]").forEach { it shouldContain "[Fork]" }
    }

    it("leaves no unfilled placeholders") {
        val contexts = listOf(
            plain.copy(isRevenge = true),
            plain.copy(endedStreak = 3),
            plain.copy(killerStreak = 3)
        )
        contexts.forEach { ctx ->
            generate(ctx, "[Fork]").forEach { it shouldNotContain "{" }
        }
        generate(plain, "[Fork]").forEach { it shouldNotContain "{" }
    }
})
