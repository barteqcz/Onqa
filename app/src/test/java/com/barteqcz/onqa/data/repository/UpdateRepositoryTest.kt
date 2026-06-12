package com.barteqcz.onqa.data.repository

import com.barteqcz.onqa.data.remote.GitHubApiService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private val apiService = object : GitHubApiService {
        override suspend fun getLatestRelease() = throw NotImplementedError()
    }
    private val repository = UpdateRepository(apiService)

    @Test
    fun `isVersionNewer should return true when latest is newer`() {
        assertTrue(repository.isVersionNewer("1.0.0", "1.1.0"))
        assertTrue(repository.isVersionNewer("1.1.0", "1.1.1"))
        assertTrue(repository.isVersionNewer("1.1.0", "2.0.0"))
        assertTrue(repository.isVersionNewer("1.1", "1.1.1"))
    }

    @Test
    fun `isVersionNewer should return false when latest is older or same`() {
        assertFalse(repository.isVersionNewer("1.1.0", "1.0.0"))
        assertFalse(repository.isVersionNewer("1.1.0", "1.1.0"))
        assertFalse(repository.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(repository.isVersionNewer("1.1.1", "1.1"))
    }
}
