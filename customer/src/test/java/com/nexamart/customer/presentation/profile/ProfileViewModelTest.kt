package com.nexamart.customer.presentation.profile

import com.nexamart.customer.core.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deletionClearsToSuccessAndIgnoresRepeatedTaps() = runTest(dispatcher) {
        var requests = 0
        val viewModel = ProfileViewModel { requests += 1 }

        viewModel.deleteAccount()
        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(1, requests)
        assertEquals(AccountDeletionState.Deleted, viewModel.deletion.value)
    }

    @Test
    fun deletionExposesFriendlyBackendFailureForRetry() = runTest(dispatcher) {
        val viewModel = ProfileViewModel { throw ApiException("Account deletion is temporarily unavailable.", 503) }

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(
            AccountDeletionState.Error("Account deletion is temporarily unavailable."),
            viewModel.deletion.value,
        )
    }
}
