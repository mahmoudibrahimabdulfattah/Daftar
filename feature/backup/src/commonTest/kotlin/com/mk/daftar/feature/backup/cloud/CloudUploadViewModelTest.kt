package com.mk.daftar.feature.backup.cloud

import com.mk.daftar.core.common.Outcome
import com.mk.daftar.core.domain.cloud.CloudAccount
import com.mk.daftar.core.domain.cloud.CloudAccountState
import com.mk.daftar.core.domain.cloud.CloudAvailability
import com.mk.daftar.core.domain.cloud.CloudBackupRepository
import com.mk.daftar.core.domain.cloud.CloudBackupVersion
import com.mk.daftar.core.domain.cloud.CloudDownload
import com.mk.daftar.core.domain.cloud.CloudError
import com.mk.daftar.core.domain.cloud.CloudListing
import com.mk.daftar.core.domain.cloud.CloudProvider
import com.mk.daftar.core.domain.cloud.CloudUploadPhase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CloudUploadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class UploadRepository(signedIn: Boolean) : CloudBackupRepository {
        private val accountState = MutableStateFlow(if (signedIn) CloudAccount("uid-1", CloudProvider.GOOGLE) else null)
        var result: Outcome<CloudBackupVersion, CloudError> = Outcome.Failure(CloudError.FAILED)
        var gate: CompletableDeferred<Unit>? = null
        var seenPassword: ByteArray? = null
        override val availability: CloudAvailability = CloudAvailability.AVAILABLE
        override val provider: CloudProvider = CloudProvider.GOOGLE
        override val account: Flow<CloudAccount?> get() = accountState

        override suspend fun signIn(): Outcome<CloudAccount, CloudError> {
            accountState.value = CloudAccount("uid-1", CloudProvider.GOOGLE)
            return Outcome.Success(CloudAccount("uid-1", CloudProvider.GOOGLE))
        }

        override suspend fun signOut() = Unit

        override suspend fun listVersions(): Outcome<CloudListing, CloudError> = Outcome.Success(
            CloudListing(
                CloudAccount("uid-1", CloudProvider.GOOGLE),
                CloudAccountState.ACTIVE,
                listOf(null, null, null),
            ),
        )

        override suspend fun download(version: CloudBackupVersion): Outcome<CloudDownload, CloudError> =
            Outcome.Failure(CloudError.FAILED)

        override suspend fun deleteVersion(version: CloudBackupVersion): Outcome<Unit, CloudError> =
            Outcome.Success(Unit)

        override suspend fun upload(
            password: ByteArray,
            onPhase: (CloudUploadPhase) -> Unit,
        ): Outcome<CloudBackupVersion, CloudError> {
            seenPassword = password
            onPhase(CloudUploadPhase.PREPARING)
            gate?.await()
            onPhase(CloudUploadPhase.COMMITTING)
            return result
        }

        override suspend fun deleteAccount(): Outcome<Unit, CloudError> = Outcome.Success(Unit)
    }

    private val validPassword = "correct horse battery"

    @Test
    fun aSignedOutUserIsAskedToSignInFirst() = runTest {
        val viewModel = CloudUploadViewModel(UploadRepository(signedIn = false))
        advanceUntilIdle()
        assertEquals(CloudUploadStep.SIGN_IN, viewModel.state.value.step)
        viewModel.onIntent(CloudUploadIntent.SignIn)
        advanceUntilIdle()
        assertEquals(CloudUploadStep.PASSWORD, viewModel.state.value.step)
    }

    @Test
    fun aShortPasswordIsNotSubmitted() = runTest {
        val repository = UploadRepository(signedIn = true)
        val viewModel = CloudUploadViewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(CloudUploadIntent.PasswordChanged("short"))
        viewModel.onIntent(CloudUploadIntent.ConfirmChanged("short"))
        viewModel.onIntent(CloudUploadIntent.Submit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.submitAttempted)
        assertEquals(CloudUploadOperation.Idle, viewModel.state.value.operation)
        assertEquals(null, repository.seenPassword)
    }

    @Test
    fun aValidPasswordCommitsAndDropsTheDraft() = runTest {
        val repository = UploadRepository(signedIn = true)
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.result = Outcome.Success(CloudBackupVersion(0, "id", 200, "0".repeat(64), null))
        val viewModel = CloudUploadViewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(CloudUploadIntent.PasswordChanged(validPassword))
        viewModel.onIntent(CloudUploadIntent.ConfirmChanged(validPassword))
        viewModel.onIntent(CloudUploadIntent.Submit)
        advanceUntilIdle()
        assertEquals(CloudUploadOperation.Preparing, viewModel.state.value.operation)
        assertEquals("", viewModel.state.value.password)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.operation is CloudUploadOperation.Complete)
        assertTrue(repository.seenPassword != null)
    }

    @Test
    fun aQuotaFailureIsShownAndCanBeAcknowledged() = runTest {
        val repository = UploadRepository(signedIn = true)
        repository.result = Outcome.Failure(CloudError.QUOTA_EXCEEDED)
        val viewModel = CloudUploadViewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(CloudUploadIntent.PasswordChanged(validPassword))
        viewModel.onIntent(CloudUploadIntent.ConfirmChanged(validPassword))
        viewModel.onIntent(CloudUploadIntent.Submit)
        advanceUntilIdle()
        assertEquals(CloudUploadOperation.Failed(CloudError.QUOTA_EXCEEDED), viewModel.state.value.operation)
        viewModel.onIntent(CloudUploadIntent.Acknowledge)
        assertEquals(CloudUploadOperation.Idle, viewModel.state.value.operation)
    }

    @Test
    fun privacyInvalidationClearsThePasswordDrafts() = runTest {
        val viewModel = CloudUploadViewModel(UploadRepository(signedIn = true))
        advanceUntilIdle()
        viewModel.onIntent(CloudUploadIntent.PasswordChanged(validPassword))
        viewModel.onIntent(CloudUploadIntent.PrivacyInvalidated)
        assertEquals("", viewModel.state.value.password)
        assertEquals("", viewModel.state.value.confirm)
    }
}
