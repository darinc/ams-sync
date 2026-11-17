package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class PvpTrackerTest : DescribeSpec({

    val alice = UUID.randomUUID()
    val bob = UUID.randomUUID()
    val carol = UUID.randomUUID()

    var now = 0L
    fun tracker() = PvpTracker(revengeWindowMillis = 1000L, clock = { now })

    beforeEach { now = 0L }

    describe("streaks") {

        it("counts consecutive kills") {
            val t = tracker()
            t.recordKill(alice, bob).killerStreak shouldBe 1
            t.recordKill(alice, carol).killerStreak shouldBe 2
            t.recordKill(alice, bob).killerStreak shouldBe 3
        }

        it("resets the killer's streak when they die in PvP and reports the ended streak") {
            val t = tracker()
            t.recordKill(alice, bob)
            t.recordKill(alice, carol)
            val ctx = t.recordKill(carol, alice)
            ctx.endedStreak shouldBe 2
            ctx.killerStreak shouldBe 1
            t.recordKill(alice, bob).killerStreak shouldBe 1
        }

        it("resets the streak on a non-PvP death") {
            val t = tracker()
            t.recordKill(alice, bob)
            t.recordKill(alice, carol)
            t.recordDeath(alice)
            t.recordKill(alice, bob).killerStreak shouldBe 1
        }

        it("reports no ended streak when the victim had none") {
            tracker().recordKill(alice, bob).endedStreak shouldBe 0
        }
    }

    describe("revenge") {

        it("detects killing your recent killer") {
            val t = tracker()
            t.recordKill(alice, bob).isRevenge shouldBe false
            now = 500L
            t.recordKill(bob, alice).isRevenge shouldBe true
        }

        it("ignores kills outside the revenge window") {
            val t = tracker()
            t.recordKill(alice, bob)
            now = 1001L
            t.recordKill(bob, alice).isRevenge shouldBe false
        }

        it("does not count killing someone other than your killer") {
            val t = tracker()
            t.recordKill(alice, bob)
            t.recordKill(bob, carol).isRevenge shouldBe false
        }

        it("only counts revenge once") {
            val t = tracker()
            t.recordKill(alice, bob)
            t.recordKill(bob, alice).isRevenge shouldBe true
            // Alice now has a fresh grudge against Bob, but Bob killing Alice again is not revenge
            t.recordKill(bob, alice).isRevenge shouldBe false
        }

        it("lets the score go back and forth") {
            val t = tracker()
            t.recordKill(alice, bob)
            t.recordKill(bob, alice).isRevenge shouldBe true
            t.recordKill(alice, bob).isRevenge shouldBe true
        }
    }
})
