package io.github.darinc.amssync.events

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlayerDeathListenerEscapeTest : DescribeSpec({

    describe("escapeForDiscord") {

        it("escapes underscores in player names") {
            PlayerDeathListener.escapeForDiscord("Silver_Parallax was speared by Noaln_") shouldBe
                "Silver\\_Parallax was speared by Noaln\\_"
        }

        it("escapes brackets so renamed items cannot form masked links") {
            PlayerDeathListener.escapeForDiscord("[free nitro](https://evil.example)") shouldBe
                "\\[free nitro\\]\\(https://evil.example\\)"
        }

        it("leaves plain text alone") {
            PlayerDeathListener.escapeForDiscord("NothingTV died") shouldBe "NothingTV died"
        }
    }
})
