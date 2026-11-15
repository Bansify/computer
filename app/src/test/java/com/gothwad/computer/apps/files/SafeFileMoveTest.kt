package com.gothwad.computer.apps.files

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SafeFileMoveTest {
    @Test fun refusesOverwriteAndPreservesBothFiles() {
        val root = Files.createTempDirectory("move-test").toFile()
        try {
            val sourceDir = root.resolve("source").apply { mkdir() }
            val destDir = root.resolve("dest").apply { mkdir() }
            val source = sourceDir.resolve("x").apply { writeText("source") }
            val dest = destDir.resolve("x").apply { writeText("destination") }
            assertFalse(SafeFileMove.move(source, destDir))
            assertEquals("source", source.readText())
            assertEquals("destination", dest.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun preventsMovingFolderIntoItselfAndMovesToEmptyDestination() {
        val root = Files.createTempDirectory("move-test").toFile()
        try {
            val source = root.resolve("source").apply { mkdir() }
            val child = source.resolve("child").apply { mkdir() }
            val dest = root.resolve("dest").apply { mkdir() }
            assertFalse(SafeFileMove.move(source, child))
            assertTrue(source.exists())
            assertTrue(SafeFileMove.move(source, dest))
            assertFalse(source.exists())
            assertTrue(dest.resolve("source/child").isDirectory)
        } finally { root.deleteRecursively() }
    }
}
