package dev.wizpor

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanupTest {
    @Test fun fillersRemoved() {
        assertEquals("So, I think we should refactor it.", Cleanup.tidy("So, um, I think, uh we should refactor it."))
        assertEquals("Add a button.", Cleanup.tidy("Um, add a button."))
        assertEquals("Make it blue.", Cleanup.tidy("Make it blue, uh."))
    }

    @Test fun fillersKeptWhenDisabled() {
        assertEquals("Um, add a button.", Cleanup.tidy("Um, add a button.", removeFillers = false))
    }

    @Test fun realWordsContainingFillersKept() {
        assertEquals("Humble and error are words.", Cleanup.tidy("Humble and error are words."))
    }

    @Test fun scratchThat() {
        assertEquals("Make it red. Make it blue.", Cleanup.tidy("Make it red. Make it green. Scratch that. Make it blue."))
        assertEquals("Make it blue.", Cleanup.tidy("Make it green, scratch that, make it blue."))
        assertEquals("", Cleanup.tidy("Scratch that."))
    }

    @Test fun customWords() {
        val words = listOf("useEffect", "Supabase", "Next.js", "Tailwind")
        assertEquals("Wrap it in a useEffect hook.", Cleanup.tidy("Wrap it in a use effect hook.", words))
        assertEquals("Store it in Supabase, then deploy.", Cleanup.tidy("Store it in supa base, then deploy.", words))
        assertEquals("Use Next.js and Tailwind.", Cleanup.tidy("Use next js and tailwind.", words))
    }

    @Test fun capitalisesSentences() {
        assertEquals("Hello. World? Yes!", Cleanup.tidy("hello. world? yes!"))
        assertEquals("Version 2.5 is out.", Cleanup.tidy("version 2.5 is out."))
    }

    @Test fun emptyInput() {
        assertEquals("", Cleanup.tidy("   "))
    }
}
