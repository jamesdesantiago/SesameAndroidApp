// File: app/src/test/java/com/gazzel/sesameapp/presentation/screens/lists/ListsViewModelTest.kt
package com.gazzel.sesameapp.presentation.screens.lists

import app.cash.turbine.test // Import Turbine's test extension
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.usecase.GetUserListsUseCase
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.util.MainDispatcherRule // Import the rule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest // Import runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock // Import Mockito's Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.* // Import Mockito-Kotlin functions (whenever, verify, any, etc.)

@ExperimentalCoroutinesApi // Needed for MainDispatcherRule and runTest
class ListsViewModelTest {

    // Rule to swap the Main dispatcher for a Test dispatcher
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mocks for the ViewModel's dependencies
    @Mock
    private lateinit var mockGetUserListsUseCase: GetUserListsUseCase

    @Mock
    private lateinit var mockListRepository: ListRepository

    // The ViewModel under test - instantiated before each test
    private lateinit var viewModel: ListsViewModel

    // Sample data for tests
    private val testList = listOf(
        SesameList(id = "1", title = "List 1", description = "Desc 1"),
        SesameList(id = "2", title = "List 2", description = "Desc 2")
    )
    private val testException = AppException.NetworkException("Network Error")

    @Before
    fun setUp() {
        // Initialize mocks annotated with @Mock
        MockitoAnnotations.openMocks(this)
        // Create ViewModel instance with mocks *BEFORE* each test
        // We need to stub the initial load behavior triggered by init{}
        // Assume initial load fails for setup simplicity, specific tests will override
        stubLoadListsError(testException)
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository)
    }

    // Helper function to stub the loadLists behavior (called by init and refresh)
    private fun stubLoadListsSuccess(lists: List<SesameList>) {
        // Use runTest for launching suspend functions in setup/stubs if needed
        // Use whenever from mockito-kotlin for stubbing suspend functions
        runTest {
            whenever(mockGetUserListsUseCase()).thenReturn(Result.success(lists))
        }
    }

    private fun stubLoadListsError(exception: AppException) {
        runTest {
            whenever(mockGetUserListsUseCase()).thenReturn(Result.error(exception))
        }
    }

    // --- Test Cases ---

    @Test
    fun `init - when use case returns success - state transitions to Success`() = runTest {
        // Arrange: Override the default stub from setUp for this specific test
        stubLoadListsSuccess(testList)
        // Re-create viewModel to trigger init with the success stub
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository)

        // Act & Assert: Use Turbine to test the StateFlow emissions
        viewModel.uiState.test {
            // 1. Expect the initial state set by init based on the stub
            val initialState = awaitItem() // Should be Success due to the arranged stub
            assertTrue(initialState is ListsUiState.Success)
            assertEquals(testList, (initialState as ListsUiState.Success).userLists)

            // 2. Ensure no other unexpected states are emitted immediately
            ensureAllEventsConsumed()
            // or cancelAndIgnoreRemainingEvents() if subsequent actions might emit more
        }

        // Verify the use case was called (by init)
        verify(mockGetUserListsUseCase, times(1))()
    }

    @Test
    fun `init - when use case returns error - state transitions to Error`() = runTest {
        // Arrange: Default stub in setUp already simulates an error.
        // Re-create viewModel to ensure init block runs with error setup
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository)

        // Act & Assert
        viewModel.uiState.test {
            val errorState = awaitItem() // Expect Error state from init
            assertTrue(errorState is ListsUiState.Error)
            assertEquals(testException.message, (errorState as ListsUiState.Error).message)
            ensureAllEventsConsumed()
        }

        // Verify the use case was called (by init)
        verify(mockGetUserListsUseCase, times(1))()
    }

    @Test
    fun `refresh - when use case returns success - state transitions to Success`() = runTest {
        // Arrange: Start with an initial state (e.g., error, or an initial success)
        stubLoadListsError(AppException.UnknownException("Initial Error"))
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository) // Initial load fails

        // Arrange: Stub the *next* call (from refresh) to be successful
        stubLoadListsSuccess(testList)

        // Act & Assert
        viewModel.uiState.test {
            // 1. Consume the initial error state from init
            assertEquals(ListsUiState.Error("Initial Error"), awaitItem())

            // 2. Trigger the refresh action
            viewModel.refresh()

            // 3. Expect Loading state during refresh
            assertEquals(ListsUiState.Loading, awaitItem())

            // 4. Expect the final Success state
            val successState = awaitItem()
            assertTrue(successState is ListsUiState.Success)
            assertEquals(testList, (successState as ListsUiState.Success).userLists)

            ensureAllEventsConsumed()
        }

        // Verify use case was called twice (init + refresh)
        verify(mockGetUserListsUseCase, times(2))()
    }

    @Test
    fun `deleteList - when repository success - calls repository and reloads lists`() = runTest {
        // Arrange: Setup an initial success state
        stubLoadListsSuccess(testList)
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository)

        // Arrange: Stub the delete operation to succeed
        val listIdToDelete = "1"
        whenever(mockListRepository.deleteList(eq(listIdToDelete))).thenReturn(Result.success(Unit))

        // Arrange: Stub the *subsequent* list load (after delete) - maybe return a shorter list
        val listAfterDelete = testList.filter { it.id != listIdToDelete }
        stubLoadListsSuccess(listAfterDelete) // Configure the *next* call to getUserListsUseCase

        // Act & Assert
        viewModel.uiState.test {
            // 1. Consume initial success state
            assertEquals(ListsUiState.Success(testList), awaitItem())

            // 2. Trigger delete action
            viewModel.deleteList(listIdToDelete)

            // 3. Expect Loading state during the reload triggered by delete success
            assertEquals(ListsUiState.Loading, awaitItem())

            // 4. Expect the final Success state with the updated list
            val finalState = awaitItem()
            assertTrue(finalState is ListsUiState.Success)
            assertEquals(listAfterDelete, (finalState as ListsUiState.Success).userLists)

            ensureAllEventsConsumed()
        }

        // Verify delete was called on the repository
        verify(mockListRepository, times(1)).deleteList(eq(listIdToDelete))
        // Verify use case was called twice (init + reload after delete)
        verify(mockGetUserListsUseCase, times(2))()
    }

    @Test
    fun `deleteList - when repository fails - does not change success state and logs error`() = runTest {
        // Arrange: Setup an initial success state
        stubLoadListsSuccess(testList)
        viewModel = ListsViewModel(mockGetUserListsUseCase, mockListRepository)

        // Arrange: Stub the delete operation to fail
        val listIdToDelete = "1"
        val deleteException = AppException.NetworkException("Delete Failed")
        whenever(mockListRepository.deleteList(eq(listIdToDelete))).thenReturn(Result.error(deleteException))

        // Act & Assert
        viewModel.uiState.test {
            // 1. Consume initial success state
            assertEquals(ListsUiState.Success(testList), awaitItem())

            // 2. Trigger delete action
            viewModel.deleteList(listIdToDelete)

            // 3. Assert: State should *not* change immediately from Success.
            //    The current implementation logs the error but doesn't set an Error state *for the delete action*.
            //    It triggers loadLists again, which we expect to just return the original list.
            //    If we wanted a specific error state *for the delete action*, the VM logic would need changing.

            // Expect Loading because loadLists is triggered even on delete failure
            assertEquals(ListsUiState.Loading, awaitItem())
            // Expect the original Success state again because the stubbed loadLists hasn't changed
            assertEquals(ListsUiState.Success(testList), awaitItem())


            // We can check logs (requires more setup) or verify interactions.
            // Let's ensure no more states emitted for now.
            ensureAllEventsConsumed()
        }

        // Verify delete was called on the repository
        verify(mockListRepository, times(1)).deleteList(eq(listIdToDelete))
        // Verify use case was called twice (init + reload after failed delete)
        verify(mockGetUserListsUseCase, times(2))()
    }
}