package app.pwhs.dunebox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.pwhs.dunebox.ui.screen.AppPickerScreen
import app.pwhs.dunebox.ui.screen.HomeScreen

object Routes {
    const val HOME = "home"
    const val APP_PICKER = "app_picker"
}

@Composable
fun DuneBoxNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddApp = { navController.navigate(Routes.APP_PICKER) }
            )
        }
        composable(Routes.APP_PICKER) {
            AppPickerScreen(
                onBack = { navController.popBackStack() },
                onAppSelected = { packageName ->
                    navController.popBackStack()
                }
            )
        }
    }
}
