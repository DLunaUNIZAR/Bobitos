package com.dlunaunizar.bobitos.data.di

import com.dlunaunizar.bobitos.data.connectivity.AndroidConnectivityRepository
import com.dlunaunizar.bobitos.data.connectivity.ConnectivityRepository
import com.dlunaunizar.bobitos.data.recipeimport.HtmlFetcher
import com.dlunaunizar.bobitos.data.recipeimport.HttpUrlHtmlFetcher
import com.dlunaunizar.bobitos.data.recipeimport.JsonLdRecipeImporter
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.AccountRepository
import com.dlunaunizar.bobitos.data.repository.ActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.DataStoreActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.DataStoreOnboardingPreferenceRepository
import com.dlunaunizar.bobitos.data.repository.DataStoreReminderPreferenceRepository
import com.dlunaunizar.bobitos.data.repository.DataStoreThemePreferenceRepository
import com.dlunaunizar.bobitos.data.repository.FirebaseAccountRepository
import com.dlunaunizar.bobitos.data.repository.FirebaseAuthRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreCalendarRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreIngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreIngredientRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreMealRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreRecipeRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreShoppingRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreSpaceRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreSpaceSummaryRepository
import com.dlunaunizar.bobitos.data.repository.FirestoreTaskRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.OnboardingPreferenceRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ReminderPreferenceRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceSummaryRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.ThemePreferenceRepository
import com.dlunaunizar.bobitos.data.sync.FirestoreSyncRepository
import com.dlunaunizar.bobitos.data.sync.SyncRepository
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
    abstract fun bindAuthRepository(repository: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSpaceRepository(repository: FirestoreSpaceRepository): SpaceRepository

    @Binds
    @Singleton
    abstract fun bindShoppingRepository(repository: FirestoreShoppingRepository): ShoppingRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(repository: FirestoreTaskRepository): TaskRepository

    @Binds @Singleton
    abstract fun bindCalendarRepository(repository: FirestoreCalendarRepository): CalendarRepository

    @Binds @Singleton
    abstract fun bindMealRepository(repository: FirestoreMealRepository): MealRepository

    @Binds @Singleton
    abstract fun bindRecipeRepository(repository: FirestoreRecipeRepository): RecipeRepository

    @Binds @Singleton
    abstract fun bindIngredientRepository(repository: FirestoreIngredientRepository): IngredientRepository

    @Binds @Singleton
    abstract fun bindIngredientPrefsRepository(
        repository: FirestoreIngredientPrefsRepository,
    ): IngredientPrefsRepository

    @Binds @Singleton
    abstract fun bindHtmlFetcher(fetcher: HttpUrlHtmlFetcher): HtmlFetcher

    @Binds @Singleton
    abstract fun bindRecipeImporter(importer: JsonLdRecipeImporter): RecipeImporter

    @Binds @Singleton
    abstract fun bindSpaceSummaryRepository(repository: FirestoreSpaceSummaryRepository): SpaceSummaryRepository

    @Binds @Singleton
    abstract fun bindAccountRepository(repository: FirebaseAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindActiveSpaceRepository(repository: DataStoreActiveSpaceRepository): ActiveSpaceRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingPreferenceRepository(
        repository: DataStoreOnboardingPreferenceRepository,
    ): OnboardingPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindReminderPreferenceRepository(
        repository: DataStoreReminderPreferenceRepository,
    ): ReminderPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindThemePreferenceRepository(
        repository: DataStoreThemePreferenceRepository,
    ): ThemePreferenceRepository

    @Binds
    @Singleton
    abstract fun bindConnectivityRepository(repository: AndroidConnectivityRepository): ConnectivityRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(repository: FirestoreSyncRepository): SyncRepository
}
