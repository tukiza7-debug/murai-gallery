package com.murai.gallery

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the v2.0.2 hotfix.
 *
 * Every ViewModel in Murai takes an [com.murai.gallery.AppContainer]; the Compose
 * `viewModel()` helper without an explicit `factory` argument falls back to the
 * reflection-based default factory, which requires a no-arg constructor that these
 * ViewModels do not have. That crashed the app at first composition
 * ("Cannot create an instance of class ...") ever since v2.0.0.
 *
 * This test scans the Kotlin sources and fails whenever a `viewModel(...)` call
 * site does not pass `factory = ...` (i.e. [com.murai.gallery.ui.components.muraiFactory]).
 */
class ViewModelFactoryCallSiteTest {

    private fun locateSourceRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = dir?.resolve("src/main/kotlin")
            if (candidate != null && candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    /** Returns the balanced-paren argument list starting at '(' or null if unbalanced. */
    private fun extractArgs(text: String, openParen: Int): String? {
        var depth = 0
        for (i in openParen until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(openParen + 1, i)
                }
            }
        }
        return null
    }

    @Test
    fun everyViewModelCallSitePassesAnExplicitFactory() {
        val root = locateSourceRoot()
        assumeTrue("Kotlin sources not found on disk; skipping source scan", root != null)

        val offenders = mutableListOf<String>()
        root!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                var idx = text.indexOf("viewModel(")
                while (idx != -1) {
                    val before = if (idx == 0) ' ' else text[idx - 1]
                    val isCallSite = !before.isLetterOrDigit() && before != '_'
                    val lineNo = text.take(idx).count { it == '\n' } + 1
                    val lineStart = text.lastIndexOf('\n', (idx - 1).coerceAtLeast(0)) + 1
                    val lineText = text.substring(lineStart).lineSequence().firstOrNull() ?: ""
                    val commentedOut = lineText.trimStart().startsWith("//")
                    if (isCallSite && !commentedOut) {
                        val args = extractArgs(text, idx + "viewModel".length)
                        // Unbalanced (inside a string/comment) is tolerated; a real
                        // call site is balanced. Missing "factory" is the bug.
                        if (args != null && !args.contains("factory")) {
                            offenders += "${file.relativeTo(root).path}:$lineNo"
                        }
                    }
                    idx = text.indexOf("viewModel(", idx + 1)
                }
            }

        assertTrue(
            "Factory-less viewModel() call(s) found — every ViewModel needs AppContainer, " +
                "so each call site must pass factory = muraiFactory { ... } : $offenders",
            offenders.isEmpty()
        )
    }
}
