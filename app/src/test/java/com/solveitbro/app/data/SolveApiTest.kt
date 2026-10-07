package com.solveitbro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolveApiTest {
    @Test
    fun `parses a solution and ignores unknown fields`() {
        val body = """
            {"status":"solved","subject":"Maths","question":"2 + 3","steps":[{"title":"Add","explanation":"2 + 3 = 5"}],
             "finalAnswer":"5","tip":null,"extra":1}
        """.trimIndent()
        val result = parseSolveResponse(200, body) as SolveResult.Success
        assertEquals("5", result.solution.finalAnswer)
        assertEquals(1, result.solution.steps.size)
        assertTrue(result.solution.isSolved)
    }

    @Test
    fun `maps error statuses`() {
        assertEquals(SolveResult.LimitReached, parseSolveResponse(429, """{"error":"daily_limit_reached"}"""))
        assertEquals(SolveResult.CannotHelp, parseSolveResponse(422, "{}"))
        assertEquals(SolveResult.Failed(network = false), parseSolveResponse(502, "{}"))
        assertEquals(SolveResult.Failed(network = false), parseSolveResponse(200, "not json"))
    }

    @Test
    fun `request omits missing OCR text, which the backend rejects as null`() {
        val json = encodeSolveRequest(SolveRequestBody(image = "abc", mediaType = "image/jpeg", language = "hi"))
        assertFalse(json.contains("ocrText"))
        assertTrue(json.contains("\"mode\":\"standard\""))
    }
}
