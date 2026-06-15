package neth.iecal.curbox.blockers.uihider.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Off-device tests for the UIHider scripting language: lexer + parser + interpreter + builtins,
 * plus the execution budget. Uses a stub [RuntimeApi] so no Android dependencies are needed.
 */
class ScriptLanguageTest {

    /** Captures `log()` output and `draw()` calls; routes everything else to [Builtins]. */
    private class StubApi : RuntimeApi {
        val log = StringBuilder()
        val draws = ArrayList<List<Any?>>()

        override fun provideGlobals(): Map<String, Any?> = mapOf(
            "app" to "com.test.app",
            "screen" to mapOf("width" to 1080.0, "height" to 1920.0),
            "event" to mapOf("type" to "content", "package" to "com.test.app")
        )

        override fun callFunction(name: String, args: List<Any?>, named: Map<String, Any?>): Any? = when (name) {
            "log" -> { log.append(args.joinToString(" ") { Values.stringify(it) }).append('\n'); null }
            "draw" -> { draws.add(args); null }
            else -> {
                val r = Builtins.tryCall(name, args)
                if (r === Builtins.UNKNOWN) throw ScriptError("unknown function '$name'")
                r
            }
        }
    }

    private fun run(source: String): StubApi {
        val api = StubApi()
        Interpreter(api, Budget()).run(Parser.parse(source))
        return api
    }

    @Test fun arithmeticPrecedence() {
        assertEquals("14\n", run("log(2 + 3 * 4)").log.toString())
    }

    @Test fun forLoopWithRange() {
        val src = "s = 0\nfor i in 0..5 { s = s + i }\nlog(s)"
        assertEquals("10\n", run(src).log.toString())
    }

    @Test fun whileWithBreakAndContinue() {
        val src = """
            i = 0
            total = 0
            while true {
                i = i + 1
                if i > 10 { break }
                if i % 2 == 0 { continue }
                total = total + i
            }
            log(total)
        """.trimIndent()
        assertEquals("25\n", run(src).log.toString())  // 1+3+5+7+9
    }

    @Test fun userFunctionsAndRecursion() {
        val src = """
            fn fact(n) {
                if n <= 1 { return 1 }
                return n * fact(n - 1)
            }
            log(fact(5))
        """.trimIndent()
        assertEquals("120\n", run(src).log.toString())
    }

    @Test fun conditionalsAndNullChecks() {
        val src = """
            a = null
            b = 5
            if a == null and b > 3 { log("ok") } else { log("no") }
        """.trimIndent()
        assertEquals("ok\n", run(src).log.toString())
    }

    @Test fun stringConcatAndBuiltins() {
        assertEquals("v=3\n", run("""log("v=" + 3)""").log.toString())
        assertEquals("7\n", run("log(max(3, 7, 2))").log.toString())
        assertEquals("5\n", run("log(clamp(9, 1, 5))").log.toString())
        assertEquals("4\n", run("log(floor(4.8))").log.toString())
    }

    @Test fun drawReceivesComputedGeometry() {
        val src = "draw(0, 100, screen.width, 200 + 50)"
        val api = run(src)
        assertEquals(1, api.draws.size)
        assertEquals(listOf(0.0, 100.0, 1080.0, 250.0), api.draws[0])
    }

    @Test fun topLevelReturnEndsScript() {
        val src = "log(1)\nreturn\nlog(2)"
        assertEquals("1\n", run(src).log.toString())
    }

    @Test fun infiniteLoopIsBudgetAborted() {
        try {
            run("while true { }")
            fail("expected budget to abort the run")
        } catch (e: ScriptError) {
            assertTrue(e.message!!.contains("budget"))
        }
    }

    @Test fun shippedDefaultScriptsAllParse() {
        for (script in neth.iecal.curbox.hardcoded.DEFAULT_UIHIDER_SCRIPTS) {
            try {
                Parser.parse(script.source)
            } catch (e: ScriptError) {
                fail("Default script '${script.id}' failed to parse: ${e.message}")
            }
        }
    }

    @Test fun pathWalkHelperRunsWithoutHostFunctions() {
        // The Reddit-style step()/path pattern should at least execute (root() returns null here).
        val src = """
            fn step(node, cls, idx) {
                if node == null { return null }
                count = 0
                for c in node.children() {
                    if c.class == cls {
                        if count == idx { return c }
                        count = count + 1
                    }
                }
                return null
            }
            n = null
            for seg in [["A", 0], ["B", 1]] {
                n = step(n, seg[0], seg[1])
            }
            log(n == null)
        """.trimIndent()
        assertEquals("true\n", run(src).log.toString())
    }

    @Test fun syntaxErrorReportsLine() {
        try {
            Parser.parse("x = \nif {")
            fail("expected a parse error")
        } catch (e: ScriptError) {
            assertTrue(e.line > 0)
        }
    }
}
