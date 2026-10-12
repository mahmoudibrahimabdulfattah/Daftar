package com.mk.daftar.feature.cloud

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CloudViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.model(repository: FakeCloudRepository, mode: CloudMode = CloudMode.SETTINGS): CloudViewModel {
        val viewModel = CloudViewModel(repository, mode)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun unavailableWhenFirebaseIsNotConfigured() = runTest {
        val viewModel = model(FakeCloudRepository(availability = CloudAvailability.UNAVAILABLE))
        assertEquals(CloudView.Unavailable, viewModel.state.value.view)
    }

    @Test
    fun signedOutWhenNoAccountIsSignedIn() = runTest {
        val viewModel = model(FakeCloudRepository(signedIn = false))
        assertEquals(CloudView.SignedOut, viewModel.state.value.view)
    }

    @Test
    fun anEmptyListingShowsTheEmptyState() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(emptyListing())
        val viewModel = model(repository)
        assertEquals(CloudView.Empty, viewModel.state.value.view)
    }

    @Test
    fun aListingWithVersionsShowsThemInSlotOrder() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0), null, version(2)))
        val viewModel = model(repository)
        assertEquals(CloudView.Versions, viewModel.state.value.view)
        assertEquals(listOf(0, 2), viewModel.state.value.versions.map { it.slot })
    }

    @Test
    fun aDeletingAccountShowsIncompleteDeletion() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(state = CloudAccountState.DELETING))
        val viewModel = model(repository)
        assertEquals(CloudView.DeletionIncomplete, viewModel.state.value.view)
    }

    @Test
    fun offlineAndQuotaFailuresAreProblemStates() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Failure(CloudError.OFFLINE)
        val offline = model(repository)
        assertEquals(CloudView.Problem(CloudError.OFFLINE), offline.state.value.view)

        repository.listing = Outcome.Failure(CloudError.QUOTA_EXCEEDED)
        val quota = model(repository)
        assertEquals(CloudView.Problem(CloudError.QUOTA_EXCEEDED), quota.state.value.view)
    }

    @Test
    fun aCancelledSignInIsNotAProblem() = runTest {
        val repository = FakeCloudRepository(signedIn = false)
        repository.signInResult = Outcome.Failure(CloudError.CANCELLED)
        val viewModel = model(repository)
        viewModel.onIntent(CloudIntent.SignIn)
        advanceUntilIdle()
        assertNull(viewModel.state.value.problem)
        assertNull(viewModel.state.value.busy)
    }

    @Test
    fun signingInShowsBusyUntilTheListingLoads() = runTest {
        val repository = FakeCloudRepository(signedIn = false)
        val viewModel = model(repository)
        repository.gate = CompletableDeferred(Unit)
        viewModel.onIntent(CloudIntent.SignIn)
        advanceUntilIdle()
        assertEquals(CloudView.Empty, viewModel.state.value.view)
        assertNull(viewModel.state.value.busy)
    }

    @Test
    fun deleteAccountShowsBusyWhileRunning() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        val viewModel = model(repository)
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        viewModel.onIntent(CloudIntent.ConfirmDeleteAccount)
        advanceUntilIdle()
        assertEquals(CloudBusy.DELETING_ACCOUNT, viewModel.state.value.busy)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(CloudView.SignedOut, viewModel.state.value.view)
    }

    @Test
    fun deletingAVersionNeedsItsOwnConfirmation() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        val viewModel = model(repository)
        viewModel.onIntent(CloudIntent.RequestDeleteVersion(version(0)))
        assertEquals(version(0), viewModel.state.value.pendingVersion)
        viewModel.onIntent(CloudIntent.DismissConfirmation)
        assertNull(viewModel.state.value.pendingVersion)
        assertTrue("deleteVersion" !in repository.calls)
    }

    @Test
    fun aChangedVersionReloadsTheListing() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        val viewModel = model(repository)
        repository.deleteVersionResult = Outcome.Failure(CloudError.CHANGED)
        viewModel.onIntent(CloudIntent.RequestDeleteVersion(version(0)))
        viewModel.onIntent(CloudIntent.ConfirmDeleteVersion)
        advanceUntilIdle()
        assertEquals(CloudError.CHANGED, viewModel.state.value.problem)
        assertEquals(2, repository.calls.count { it == "list" })
    }

    @Test
    fun downloadIsOfferedOnlyOnTheRecoverySurface() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        val viewModel = model(repository, mode = CloudMode.SETTINGS)
        viewModel.onIntent(CloudIntent.Download(version(0)))
        advanceUntilIdle()
        assertTrue("download" !in repository.calls)
    }

    @Test
    fun aRecoveryDownloadEmitsARestoreEffect() = runTest {
        val repository = FakeCloudRepository(signedIn = true)
        repository.listing = Outcome.Success(listingWith(version(0)))
        repository.downloadResult = Outcome.Success(CloudDownload(version(0), ByteArray(84)))
        val viewModel = model(repository, mode = CloudMode.RECOVERY)
        viewModel.onIntent(CloudIntent.Download(version(0)))
        advanceUntilIdle()
        assertNull(viewModel.state.value.busy)
        assertEquals(CloudMode.RECOVERY, viewModel.state.value.mode)
        assertTrue("download" in repository.calls)
    }

    @Test
    fun aFailedDownloadShowsItsProblem() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        repository.downloadResult = Outcome.Failure(CloudError.UNVERIFIED)
        val viewModel = model(repository, mode = CloudMode.RECOVERY)
        viewModel.onIntent(CloudIntent.Download(version(0)))
        advanceUntilIdle()
        assertEquals(CloudError.UNVERIFIED, viewModel.state.value.problem)
    }

    @Test
    fun privacyInvalidationDropsEveryConfirmation() = runTest {
        val repository = FakeCloudRepository()
        repository.listing = Outcome.Success(listingWith(version(0)))
        val viewModel = model(repository)
        viewModel.onIntent(CloudIntent.RequestDeleteAccount)
        viewModel.onIntent(CloudIntent.PrivacyInvalidated)
        assertEquals(false, viewModel.state.value.confirmAccountDelete)
        assertIs<CloudView.Versions>(CloudView.Versions)
    }
}
