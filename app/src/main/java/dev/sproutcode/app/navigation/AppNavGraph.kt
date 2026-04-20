package dev.sproutcode.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sproutcode.app.ui.servercreate.ServerCreateScreen
import dev.sproutcode.app.ui.serveredit.ServerEditScreen
import dev.sproutcode.app.ui.serverlist.ServerListScreen
import dev.sproutcode.app.ui.settings.SettingsScreen
import dev.sproutcode.app.ui.terminal.TerminalScreen

private const val ROUTE_LIST         = "serverList"
private const val ROUTE_EDIT_NEW     = "serverEdit"
private const val ROUTE_EDIT_EXIST   = "serverEdit/{id}"
private const val ROUTE_TERMINAL     = "terminal/{id}"
private const val ROUTE_SETTINGS     = "settings"
private const val ROUTE_HETZNER_NEW  = "hetznerCreate"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LIST) {

        composable(ROUTE_LIST) {
            ServerListScreen(
                onConnect       = { id -> navController.navigate("terminal/$id") },
                onAddManual     = { navController.navigate(ROUTE_EDIT_NEW) },
                onCreateHetzner = { navController.navigate(ROUTE_HETZNER_NEW) },
                onEdit          = { id -> navController.navigate("serverEdit/$id") },
                onSettings      = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_EDIT_NEW) {
            ServerEditScreen(
                serverId = null,
                onBack   = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_EDIT_EXIST,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id")
            ServerEditScreen(
                serverId = id,
                onBack   = { navController.popBackStack() }
            )
        }

        composable(ROUTE_HETZNER_NEW) {
            ServerCreateScreen(
                onCreated = { serverId ->
                    navController.popBackStack(ROUTE_LIST, inclusive = false)
                    navController.navigate("terminal/$serverId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_TERMINAL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            TerminalScreen(
                serverId     = id,
                onDisconnect = { navController.popBackStack() }
            )
        }
    }
}
