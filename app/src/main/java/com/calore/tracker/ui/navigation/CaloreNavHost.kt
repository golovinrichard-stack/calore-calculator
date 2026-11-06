package com.calore.tracker.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calore.tracker.R
import com.calore.tracker.ui.about.AboutScreen
import com.calore.tracker.ui.addfood.AddFoodScreen
import com.calore.tracker.ui.backup.BackupScreen
import com.calore.tracker.ui.calculator.CalculatorScreen
import com.calore.tracker.ui.customfood.CustomFoodScreen
import com.calore.tracker.ui.fooddetail.FoodDetailScreen
import com.calore.tracker.ui.goals.GoalsScreen
import com.calore.tracker.ui.today.TodayScreen
import com.calore.tracker.ui.weight.WeightScreen

private enum class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    DIARY(Routes.DIARY, R.string.nav_diary, R.drawable.ic_nav_diary),
    WEIGHT(Routes.WEIGHT, R.string.nav_weight, R.drawable.ic_nav_weight),
    GOALS(Routes.GOALS, R.string.nav_goals, R.drawable.ic_nav_goals),
}

private val dateAndMealArgs = listOf(
    navArgument(Routes.ARG_DATE) { type = NavType.StringType },
    navArgument(Routes.ARG_MEAL) { type = NavType.StringType },
)

@Composable
fun CaloreNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    // Each screen has its own Scaffold for its top bar; this outer one only hosts the
    // bottom navigation, so it leaves window insets to the inner screens.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DIARY,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.DIARY) {
                TodayScreen(onAddFood = { date, meal -> navController.navigate(Routes.addFood(date, meal)) })
            }
            composable(Routes.ADD_FOOD, arguments = dateAndMealArgs) {
                AddFoodScreen(
                    onBack = { navController.popBackStack() },
                    onFoodChosen = { foodId, date, meal ->
                        navController.navigate(Routes.foodDetail(foodId, date, meal))
                    },
                    onCreateCustom = { date, meal, name ->
                        navController.navigate(Routes.customFood(date, meal, name = name))
                    },
                )
            }
            composable(
                Routes.FOOD_DETAIL,
                arguments = dateAndMealArgs + navArgument(Routes.ARG_FOOD_ID) { type = NavType.LongType },
            ) {
                FoodDetailScreen(
                    onBack = { navController.popBackStack() },
                    onLogged = { navController.popBackStack(Routes.DIARY, inclusive = false) },
                    onEdit = { foodId, date, meal ->
                        navController.navigate(Routes.customFood(date, meal, foodId = foodId))
                    },
                    onDeleted = { navController.popBackStack() },
                )
            }
            composable(
                Routes.CUSTOM_FOOD,
                arguments = dateAndMealArgs + listOf(
                    navArgument(Routes.ARG_FOOD_ID) {
                        type = NavType.LongType
                        defaultValue = Routes.NEW_FOOD_ID
                    },
                    navArgument(Routes.ARG_NAME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                CustomFoodScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { foodId, date, meal ->
                        navController.navigate(Routes.foodDetail(foodId, date, meal)) {
                            popUpTo(Routes.CUSTOM_FOOD) { inclusive = true }
                        }
                    },
                    onUpdated = { navController.popBackStack() },
                )
            }
            composable(Routes.WEIGHT) { WeightScreen() }
            composable(Routes.GOALS) {
                GoalsScreen(
                    onOpenCalculator = { navController.navigate(Routes.CALCULATOR) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.CALCULATOR) {
                CalculatorScreen(
                    onBack = { navController.popBackStack() },
                    onApplied = { navController.popBackStack() },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
