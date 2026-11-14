package com.example

import com.example.core.BidiTextHelper
import com.example.database.ModelingTemplateEntity
import com.example.modeling.ModelingDateHelper
import com.example.modeling.ModelingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `date transitions across month, year, and leap year boundaries`() {
        // 30/11/2008 -> 01/12/2008 -> 02/12/2008 -> 03/12/2008
        assertEquals("30/11/2008", ModelingDateHelper.calculateGroupDate("30/11/2008", 0, "INCREMENT_DAY"))
        assertEquals("01/12/2008", ModelingDateHelper.calculateGroupDate("30/11/2008", 1, "INCREMENT_DAY"))
        assertEquals("02/12/2008", ModelingDateHelper.calculateGroupDate("30/11/2008", 2, "INCREMENT_DAY"))
        assertEquals("03/12/2008", ModelingDateHelper.calculateGroupDate("30/11/2008", 3, "INCREMENT_DAY"))

        // Year transition: 31/12/2025 -> 01/01/2026
        assertEquals("31/12/2025", ModelingDateHelper.calculateGroupDate("31/12/2025", 0, "INCREMENT_DAY"))
        assertEquals("01/01/2026", ModelingDateHelper.calculateGroupDate("31/12/2025", 1, "INCREMENT_DAY"))

        // Leap year 2024 vs non-leap year 2023
        assertEquals("29/02/2024", ModelingDateHelper.calculateGroupDate("28/02/2024", 1, "INCREMENT_DAY"))
        assertEquals("01/03/2024", ModelingDateHelper.calculateGroupDate("28/02/2024", 2, "INCREMENT_DAY"))
        assertEquals("01/03/2023", ModelingDateHelper.calculateGroupDate("28/02/2023", 1, "INCREMENT_DAY"))

        // Fixed date mode
        assertEquals("30/11/2008", ModelingDateHelper.calculateGroupDate("30/11/2008", 15, "FIXED"))
    }

    @Test
    fun `parent children template matches required output format and increments dates`() {
        val template = ModelingEngine.getDefaultBuiltInTemplates().first { it.templateType == "PARENT_CHILDREN" }
        val input = (1..8).joinToString("\n") { "test@example.com:sample-password" }

        val result = ModelingEngine.process(input, template)
        assertNull(result.templateValidationError)
        assertEquals(2, result.groups.size)
        assertEquals("30/11/2008", result.groups[0].dateString)
        assertEquals("01/12/2008", result.groups[1].dateString)

        val expectedFirstGroup = """
            -----------------------------------------------------
            [ 30/11/2008 ]
            -----------------------------------------------------
            Parent: test@example.com:sample-password
            ------------------------------------------------------
            1 Child: test@example.com:sample-password
            ------------------------------------------------------
            2 Child: test@example.com:sample-password
            ------------------------------------------------------
            3 Child: test@example.com:sample-password
            ------------------------------------------------------
        """.trimIndent()
        assertEquals(expectedFirstGroup, result.groups[0].formattedText)
    }

    @Test
    fun `sequential list template formats numbered items with Arabic and English numerals`() {
        val baseTemplate = ModelingEngine.getDefaultBuiltInTemplates().first { it.templateType == "SEQUENTIAL_LIST" }
        val input = (1..4).joinToString("\n") { "test@example.com:sample-password" }

        val englishResult = ModelingEngine.process(input, baseTemplate)
        val expectedEnglish = """
            -----------------------------------------------------
            1: test@example.com:sample-password
            ------------------------------------------------------
            2: test@example.com:sample-password
            ------------------------------------------------------
            3: test@example.com:sample-password
            ------------------------------------------------------
            4: test@example.com:sample-password
            ------------------------------------------------------
        """.trimIndent()
        assertEquals(expectedEnglish, englishResult.fullOutput)

        // Arabic numerals + Reverse order
        val arabicTemplate = baseTemplate.copy(useArabicNumerals = true, startNumber = 10)
        val arabicResult = ModelingEngine.process("a:1\nb:2", arabicTemplate)
        assertTrue(arabicResult.fullOutput.contains("١٠: a:1"))
        assertTrue(arabicResult.fullOutput.contains("١١: b:2"))
    }

    @Test
    fun `modeling engine handles 10, 50, 1000, and 10000 records plus duplicates and invalid lines without dropping data`() {
        val template = ModelingEngine.getDefaultBuiltInTemplates().first()

        for (size in listOf(10, 50, 1000, 10000)) {
            val largeInput = (1..size).joinToString("\n") { "user$it@nova.io:secretPass#$it" }
            val res = ModelingEngine.process(largeInput, template)
            assertEquals(size, res.validCount)
            assertEquals(0, res.invalidCount)
            assertTrue(res.fullOutput.isNotEmpty())
        }

        // Test duplicate detection and invalid line identification without silent deletion
        val mixedInput = "valid@nova.io:123\n\nvalid@nova.io:123\n:broken_missing_key\nmulti:colon:record:ok"
        val diagResult = ModelingEngine.process(mixedInput, template)
        assertEquals(5, diagResult.totalInputLines)
        assertEquals(1, diagResult.emptyLineCount)
        assertEquals(1, diagResult.duplicateCount)
        assertEquals(1, diagResult.invalidCount)
        assertEquals(3, diagResult.validCount)
        // Verify invalid line is still preserved in output (never silently deleted)
        assertTrue(diagResult.fullOutput.contains(":broken_missing_key"))
        assertTrue(diagResult.fullOutput.contains("multi:colon:record:ok"))
    }

    @Test
    fun `custom template validation catches unbalanced braces and unknown placeholders`() {
        val badBraces = ModelingTemplateEntity(
            name = "Broken",
            templateType = "CUSTOM",
            itemPattern = "{number: {record}"
        )
        assertNotNull(ModelingEngine.validateTemplate(badBraces))

        val unknownPlaceholder = ModelingTemplateEntity(
            name = "Unknown Token",
            templateType = "CUSTOM",
            itemPattern = "{number}: {unknown_magic}"
        )
        assertNotNull(ModelingEngine.validateTemplate(unknownPlaceholder))

        val validCustom = ModelingTemplateEntity(
            name = "Custom Group Format",
            templateType = "CUSTOM",
            headerPattern = "=== Group {group_number} [{date}] ===",
            parentPattern = "Leader -> {parent}",
            itemPattern = "Member #{number} -> {child}",
            recordsPerGroup = 3,
            startDateStr = "28/02/2024",
            dateMode = "INCREMENT_DAY"
        )
        assertNull(ModelingEngine.validateTemplate(validCustom))
        val res = ModelingEngine.process("p1:1\nc1:2\nc2:3\np2:4\nc3:5", validCustom)
        assertTrue(res.fullOutput.contains("=== Group 1 [28/02/2024] ==="))
        assertTrue(res.fullOutput.contains("=== Group 2 [29/02/2024] ==="))
        assertTrue(res.fullOutput.contains("Leader -> p1:1"))
        assertTrue(res.fullOutput.contains("Member #1 -> c1:2"))
    }

    @Test
    fun `rtl and ltr bidirectional detection handles Arabic, English, and mixed text`() {
        assertTrue(BidiTextHelper.isRtlText("مرحبا بك في تطبيق نوفا نوتس user@example.com 123"))
        assertFalse(BidiTextHelper.isRtlText("Welcome to Nova Notes مرحبا"))
        assertTrue(BidiTextHelper.resolveIsRtl("RTL", "English text forced RTL"))
        assertFalse(BidiTextHelper.resolveIsRtl("LTR", "نص عربي مفروض من اليسار"))
        assertEquals("٢٠٢٦", BidiTextHelper.formatNumber(2026, true))
    }

    @Test
    fun `showDateOnEveryGroup and combineGroupsIntoOneList work across Sequential List and Parent Children`() {
        val seqBase = ModelingEngine.getDefaultBuiltInTemplates().first { it.templateType == "SEQUENTIAL_LIST" }
        val input = (1..4).joinToString("\n") { "user$it@example.com:pass$it" }

        // Sequential List with 2 records per group, date on every group, separate cards
        val seqDatedGroups = seqBase.copy(
            recordsPerGroup = 2,
            startDateStr = "30/11/2008",
            dateMode = "INCREMENT_DAY",
            showDateOnEveryGroup = true,
            combineGroupsIntoOneList = false
        )
        val resSeparate = ModelingEngine.process(input, seqDatedGroups)
        assertEquals(2, resSeparate.groups.size)
        assertTrue(resSeparate.groups[0].formattedText.contains("[ 30/11/2008 ]"))
        assertTrue(resSeparate.groups[1].formattedText.contains("[ 01/12/2008 ]"))

        // Now combine those dated groups into one single list card
        val seqCombinedOneList = seqDatedGroups.copy(combineGroupsIntoOneList = true)
        val resCombined = ModelingEngine.process(input, seqCombinedOneList)
        assertEquals(1, resCombined.groups.size)
        assertEquals(4, resCombined.groups[0].recordIndices.size)
        assertTrue(resCombined.groups[0].formattedText.contains("[ 30/11/2008 ]"))
        assertTrue(resCombined.groups[0].formattedText.contains("[ 01/12/2008 ]"))

        // Parent / Children combined on one list
        val parentBase = ModelingEngine.getDefaultBuiltInTemplates().first { it.templateType == "PARENT_CHILDREN" }
        val parentOneList = parentBase.copy(
            recordsPerGroup = 2,
            showDateOnEveryGroup = true,
            combineGroupsIntoOneList = true
        )
        val resParentCombined = ModelingEngine.process(input, parentOneList)
        assertEquals(1, resParentCombined.groups.size)
        assertTrue(resParentCombined.fullOutput.contains("[ 30/11/2008 ]"))
        assertTrue(resParentCombined.fullOutput.contains("[ 01/12/2008 ]"))
    }
}

