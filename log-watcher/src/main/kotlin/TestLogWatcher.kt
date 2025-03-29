package com.kemonotigris

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestLogWatcher {
    private val _usersInInstanceFlow = MutableStateFlow<Set<String>>(emptySet())
    val usersInInstanceFlow: Flow<Set<String>> = _usersInInstanceFlow.asStateFlow()

    fun simulateUserJoined(userId: String) {
        println("⚠️ STARTING USER SIMULATION FOR: $userId")
        println("⚠️ Current users before: ${_usersInInstanceFlow.value}")

        val currentUsers = _usersInInstanceFlow.value.toMutableSet()
        currentUsers.add(userId)
        _usersInInstanceFlow.value = currentUsers

        println("⚠️ Current users after: ${_usersInInstanceFlow.value}")
        println("⚠️ SIMULATION COMPLETED")
    }

    // Method to simulate a user leaving
    fun simulateUserLeft(userId: String) {
        val currentUsers = _usersInInstanceFlow.value.toMutableSet()
        currentUsers.remove(userId)
        _usersInInstanceFlow.value = currentUsers
        println("Simulated user left: $userId")
    }
}