package com.andres.walksecurity.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andres.walksecurity.data.repository.AuthRepository
import com.andres.walksecurity.ui.auth.LoginScreen
import com.andres.walksecurity.ui.auth.RegisterScreen
import com.andres.walksecurity.ui.contacts.ContactsScreen
import com.andres.walksecurity.ui.home.HomeScreen
import com.andres.walksecurity.ui.watch.WatchEmulatorScreen
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable internal data object LoginRoute
@Serializable internal data object RegisterRoute
@Serializable internal data object HomeRoute
@Serializable internal data object ContactsRoute
@Serializable internal data object WatchRoute

private sealed interface RootState {
    data object Loading : RootState
    data object LoggedOut : RootState
    data object LoggedIn : RootState
}

/**
 * La sesión decide qué grafo se muestra: al iniciar o cerrar sesión se cambia de grafo
 * y la pila de navegación anterior se descarta (no se puede "volver" al login).
 */
@Composable
fun WalkSecurityRoot(authRepository: AuthRepository) {
    val rootFlow = remember(authRepository) {
        authRepository.session.map { if (it == null) RootState.LoggedOut else RootState.LoggedIn }
    }
    val root by rootFlow.collectAsStateWithLifecycle(initialValue = RootState.Loading)

    when (root) {
        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        RootState.LoggedOut -> AuthGraph()
        RootState.LoggedIn -> MainGraph()
    }
}

@Composable
private fun AuthGraph() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(onGoToRegister = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onGoToLogin = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainGraph() {
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
