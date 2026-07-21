package com.dlunaunizar.bobitos.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSpaceSummaryRepository @Inject constructor() : SpaceSummaryRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun counts(spaceId: String): SpaceModuleCounts = coroutineScope {
        val space = firestore.collection(SPACES).document(spaceId)
        val shopping = async {
            space.collection(SHOPPING_ITEMS).whereEqualTo(FIELD_PURCHASED, false).countOrZero()
        }
        val tasks = async {
            space.collection(TASKS).whereEqualTo(FIELD_STATUS, STATUS_TODO).countOrZero()
        }
        val events = async {
            space.collection(EVENTS).whereGreaterThanOrEqualTo(FIELD_START_AT, Timestamp.now()).countOrZero()
        }
        val meals = async {
            space.collection(MEALS).whereEqualTo(FIELD_DATE, LocalDate.now().toString()).countOrZero()
        }
        SpaceModuleCounts(
            pendingShopping = shopping.await(),
            pendingTasks = tasks.await(),
            upcomingEvents = events.await(),
            todayMeals = meals.await(),
        )
    }

    private suspend fun Query.countOrZero(): Int = count().get(AggregateSource.SERVER).await().count.toInt()

    private companion object {
        const val SPACES = "spaces"
        const val SHOPPING_ITEMS = "shoppingItems"
        const val TASKS = "tasks"
        const val EVENTS = "events"
        const val MEALS = "meals"
        const val FIELD_PURCHASED = "purchased"
        const val FIELD_STATUS = "status"
        const val FIELD_START_AT = "startAt"
        const val FIELD_DATE = "date"
        const val STATUS_TODO = "TODO"
    }
}
