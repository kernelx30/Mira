package com.ai.assistance.operit.ui.main

import androidx.lifecycle.ViewModel
import com.ai.assistance.operit.ui.main.navigation.AppRouterState
import com.ai.assistance.operit.ui.main.navigation.RouteEntry

internal class MainNavigationViewModel : ViewModel() {
    private var routerState: AppRouterState? = null

    fun getOrCreateRouterState(initialEntry: RouteEntry): AppRouterState {
        routerState?.let { return it }
        return AppRouterState(initialEntry).also { routerState = it }
    }
}
