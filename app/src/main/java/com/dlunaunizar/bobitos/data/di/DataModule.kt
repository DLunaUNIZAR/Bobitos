package com.dlunaunizar.bobitos.data.di

import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.FirebaseAuthRepository
import com.dlunaunizar.bobitos.data.repository.InMemorySpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: FirebaseAuthRepository,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSpaceRepository(
        repository: InMemorySpaceRepository,
    ): SpaceRepository
}
