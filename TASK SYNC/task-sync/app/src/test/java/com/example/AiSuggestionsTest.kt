package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.example.ui.AiSuggestionsUiState
import com.example.data.api.TaskSuggestion

@RunWith(RobolectricTestRunner::class)
class AiSuggestionsTest {

    @Test
    fun testAiSuggestionsUiState_Transitions() {
        // Verify state models are correctly defined
        val successState = AiSuggestionsUiState.Success(
            suggestions = listOf(
                TaskSuggestion(
                    title = "Code Review",
                    description = "Perform peer code reviews for current sprint",
                    priority = "Medium",
                    category = "Work"
                )
            )
        )
        
        assertEquals(1, successState.suggestions.size)
        assertEquals("Code Review", successState.suggestions.first().title)
        assertEquals("Medium", successState.suggestions.first().priority)
    }
}
