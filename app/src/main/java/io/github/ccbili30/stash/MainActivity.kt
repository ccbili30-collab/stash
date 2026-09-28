package io.github.ccbili30.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ccbili30.stash.data.SettingsStore
import io.github.ccbili30.stash.ui.A11yGuideScreen
import io.github.ccbili30.stash.ui.DetailScreen
import io.github.ccbili30.stash.ui.MainScreen
import io.github.ccbili30.stash.ui.SettingsScreen
import io.github.ccbili30.stash.ui.theme.StashTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings = SettingsStore(this)
            val dynamicColor by settings.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = true)
            StashTheme(dynamicColor = dynamicColor) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onOpenDetail = { nav.navigate("detail/$it") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("detail/{id}") { backStack ->
                        val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                        DetailScreen(
                            entryId = id,
                            onBack = { nav.popBackStack() },
                            onDeleted = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenA11yGuide = { nav.navigate("a11y") },
                        )
                    }
                    composable("a11y") {
                        A11yGuideScreen(onBack = { nav.popBackStack() })
                    }
                }
                // 磁贴跳转：无障碍未开启时直达引导页
                androidx.compose.runtime.LaunchedEffect(startRoute) {
                    if (startRoute == ROUTE_A11Y_GUIDE) nav.navigate("a11y")
                }
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_A11Y_GUIDE = "a11y"
    }
}
