package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface PocketMindRoute {
    @Serializable
    data object Chat : PocketMindRoute

    @Serializable
    data object Tools : PocketMindRoute

    @Serializable
    data object Models : PocketMindRoute

    @Serializable
    data object Guide : PocketMindRoute
}
