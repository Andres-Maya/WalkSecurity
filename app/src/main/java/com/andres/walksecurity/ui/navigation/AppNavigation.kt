package com.andres.walksecurity.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andres.walksecurity.ui.contacts.ContactsScreen
import com.andres.walksecurity.ui.home.HomeScreen
import com.andres.walksecurity.ui.watch.WatchEmulatorScreen
import kotlinx.serialization.Serializable

@Serializable internal data object HomeRoute
@Serializable internal data object ContactsRoute
@Serializable internal data object WatchRoute

/** La app abre directamente en la pantalla principal: no hay inicio de sesión. */
@Composable
fun WalkSecurityRoot() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenContacts = { navController.navigate(ContactsRoute) { launchSingleTop = true } },
                onOpenWatch = { navController.navigate(WatchRoute) { launchSingleTop = true } },
            )
        }
        composable<ContactsRoute> {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable<WatchRoute> {
            WatchEmulatorScreen(onBack = { navController.popBackStack() })
        }
    }
}
