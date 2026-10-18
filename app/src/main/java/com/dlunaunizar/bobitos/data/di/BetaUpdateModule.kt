package com.dlunaunizar.bobitos.data.di

import com.dlunaunizar.bobitos.BuildConfig
import com.dlunaunizar.bobitos.data.betaupdate.BetaDistribution
import com.dlunaunizar.bobitos.data.betaupdate.DisabledBetaDistribution
import com.dlunaunizar.bobitos.data.betaupdate.FirebaseBetaDistribution
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BetaUpdateModule {
    @Provides
    @Singleton
    fun provideBetaDistribution(): BetaDistribution =
        if (BuildConfig.BETA_UPDATES_ENABLED) FirebaseBetaDistribution() else DisabledBetaDistribution
}
