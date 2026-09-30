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
import com.andres.walksecurity.core.model.Session
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
    /** Con cuenta o en modo local: la app completa está disponible. */
    data class LoggedIn(val session: Session) : RootState
}

/**
 * La sesión decide qué grafo se muestra: al iniciar o cerrar sesión se cambia de grafo
 * y la pila de navegación anterior se descarta (no se puede "volver" al login).
 */
@Composable
fun WalkSecurityRoot(authRepository: AuthRepository) {
    val rootFlow = remember(authRepository) {
        authRepository.session.map { session -> if (session == null) RootState.LoggedOut else RootState.LoggedIn(session) }
    }
    val root by rootFlow.collectAsStateWithLifecycle(initialValue = RootState.Loading)

    when (val state = root) {
        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        RootState.LoggedOut -> AuthGraph()
        is RootState.LoggedIn -> MainGraph(state.session)
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
private fun MainGraph(session: Session) {
    val navController = rememberNavController()
    val backToHome: () -> Unit = { navController.popBackStack(HomeRoute, inclusive = false) }
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenContacts = { navController.navigate(ContactsRoute) { launchSingleTop = true } },
                onOpenWatch = { navController.navigate(WatchRoute) { launchSingleTop = true } },
                onLogin = { navController.navigate(LoginRoute) { launchSingleTop = true } },
                onRegister = { navController.navigate(RegisterRoute) { launchSingleTop = true } },
            )
        }
        // Desde el modo local: al iniciar sesión o registrarse se vuelve al inicio y se suben los contactos
        composable<LoginRoute> {
            LoginScreen(
                onGoToRegister = { navController.navigate(RegisterRoute) { launchSingleTop = true } },
                onSuccess = backToHome,
                allowLocalMode = false,
            )
        }
        composable<RegisterRoute> {
            RegisterScreen(
                onGoToLogin = { navController.navigate(LoginRoute) { launchSingleTop = true } },
                onSuccess = backToHome,
                initialName = if (session.isLocal) session.user.name else "",
                initialPhone = if (session.isLocal) session.user.phone else "",
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
