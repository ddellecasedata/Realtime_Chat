package com.things5.realtimechat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.things5.realtimechat.ui.screens.AdminScreen
import com.things5.realtimechat.ui.screens.MainScreen
import com.things5.realtimechat.ui.screens.McpDebugScreen
import com.things5.realtimechat.ui.screens.McpToolsScreen
import com.things5.realtimechat.ui.screens.RealtimeDebugScreen
import com.things5.realtimechat.viewmodel.MainViewModel
import com.things5.realtimechat.viewmodel.McpToolsViewModel

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Admin : Screen("admin")
    object McpDebug : Screen("mcp_debug/{serverName}") {
        fun createRoute(serverName: String) = "mcp_debug/$serverName"
    }
    object McpTools : Screen("mcp_tools/{serverName}") {
        fun createRoute(serverName: String) = "mcp_tools/$serverName"
    }
    object RealtimeDebug : Screen("realtime_debug")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
                },
                viewModel = mainViewModel
            )
        }
        
        composable(Screen.Admin.route) {
            AdminScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToMcpDebug = { serverName ->
                    navController.navigate(Screen.McpDebug.createRoute(serverName))
                },
                onNavigateToMcpTools = { serverName ->
                    navController.navigate(Screen.McpTools.createRoute(serverName))
                },
                onNavigateToRealtimeDebug = {
                    navController.navigate(Screen.RealtimeDebug.route)
                }
            )
        }
        
        composable(
            route = Screen.McpDebug.route,
            arguments = listOf(
                navArgument("serverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serverName = backStackEntry.arguments?.getString("serverName") ?: ""
            val mcpStatus by mainViewModel.mcpServerStatus.collectAsState()
            val serverStatus = mcpStatus[serverName]
            val settings by mainViewModel.settings.collectAsState()
            val serverConfig = settings.mcpServers.find { it.name == serverName }
            
            McpDebugScreen(
                serverName = serverName,
                serverStatus = serverStatus,
                serverConfig = serverConfig,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRefresh = {
                    // Refresh is handled by the viewmodel collecting the status
                }
            )
        }
        
        composable(
            route = Screen.McpTools.route,
            arguments = listOf(
                navArgument("serverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serverName = backStackEntry.arguments?.getString("serverName") ?: ""
            
            // Create McpToolsViewModel with dependencies
            val toolsViewModel = McpToolsViewModel(
                application = mainViewModel.getApplication(),
                serverName = serverName,
                mcpBridge = mainViewModel.getMcpBridge()
            )
            
            McpToolsScreen(
                viewModel = toolsViewModel,
                serverName = serverName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.RealtimeDebug.route) {
            val debugState by mainViewModel.debugState.collectAsState()
            
            RealtimeDebugScreen(
                debugState = debugState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRefresh = {
                    // Debug state is automatically updated
                },
                onClearLogs = {
                    mainViewModel.clearDebugLogs()
                }
            )
        }
    }
}
