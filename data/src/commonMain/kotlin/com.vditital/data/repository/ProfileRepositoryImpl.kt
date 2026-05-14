package com.vditital.data.repository

import com.vditital.data.datasource.ProfileDataSource
import com.vditital.data.model.CreateProfileRequest
import com.vditital.data.model.UserProfile
import com.vditital.data.repository.state.ResultState
import com.vditital.data.util.extractUserIdFromJwt

class ProfileRepositoryImpl(
    private val profileDataSource: ProfileDataSource,
    private val authRepository: AuthRepository,
) : ProfileRepository {

    override suspend fun loadMyProfile(): ResultState<UserProfile> = runCatching {
        val jwt = requireJwt()
        val userId = extractUserIdFromJwt(jwt).takeIf { it.isNotBlank() }
            ?: error("Unable to resolve userId from token")
        profileDataSource.viewProfileByUserId(jwt, userId)
    }.fold(
        onSuccess = { ResultState.Success(it) },
        onFailure = { ResultState.Error(it) },
    )

    override suspend fun saveMyProfile(
        fullName: String,
        phone: String,
        address: String,
    ): ResultState<UserProfile> = runCatching {
        val jwt = requireJwt()
        val userId = extractUserIdFromJwt(jwt).takeIf { it.isNotBlank() }
            ?: error("Unable to resolve userId from token")

        val existing = try {
            profileDataSource.viewProfileByUserId(jwt, userId)
        } catch (_: NoSuchElementException) {
            null
        }

        if (existing == null) {
            profileDataSource.createProfile(
                jwt = jwt,
                request = CreateProfileRequest(
                    fullName = fullName,
                    phone = phone,
                    address = address,
                ),
            )
        } else {
            profileDataSource.updateProfile(
                jwt = jwt,
                profile = existing.copy(
                    fullName = fullName,
                    phone = phone,
                    address = address,
                ),
            )
        }
    }.fold(
        onSuccess = { ResultState.Success(it) },
        onFailure = { ResultState.Error(it) },
    )

    override suspend fun upgradeCurrentUserToAdmin(): ResultState<String> = runCatching {
        profileDataSource.upgradeAdmin(requireJwt())
    }.fold(
        onSuccess = { ResultState.Success(it) },
        onFailure = { ResultState.Error(it) },
    )

    private suspend fun requireJwt(): String {
        return authRepository.ensureValidJwt()
            ?.takeIf { it.isNotBlank() }
            ?: error("Not authenticated")
    }
}


