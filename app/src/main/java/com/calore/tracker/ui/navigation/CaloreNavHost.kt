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
import androidx.compose.runtime.LaunchedEffect
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
import com.calore.tracker.ui.body.BodyFatScreen
import com.calore.tracker.ui.body.BodyFatTutorialScreen
import com.calore.tracker.ui.calculator.CalculatorScreen
import com.calore.tracker.ui.checkin.CheckinScreen
import com.calore.tracker.ui.customfood.CustomFoodScreen
import com.calore.tracker.ui.fooddetail.FoodDetailScreen
import com.calore.tracker.ui.goals.GoalsScreen
import com.calore.tracker.ui.me.MeDestination
import com.calore.tracker.ui.me.MeScreen
import com.calore.tracker.ui.profile.ProfileScreen
import com.calore.tracker.ui.scanner.ScannerScreen
import com.calore.tracker.domain.MealType
import com.calore.tracker.domain.parseIsoDate
import com.calore.tracker.ui.settings.SettingsScreen
import com.calore.tracker.ui.setup.SetupScreen
import com.calore.tracker.ui.AppViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calore.tracker.ui.today.TodayScreen
import com.calore.tracker.ui.weight.WeightScreen

/** The bottom bar (M1.9): Today and Me. Plan, Kitchen and Shop join in M3–M5. */
private enum class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(Routes.DIARY, R.string.nav_today, R.drawable.ic_nav_diary),
    ME(Routes.ME, R.string.nav_me, R.drawable.ic_nav_me),
}

private val dateAndMealArgs = listOf(
    navArgument(Routes.ARG_DATE) { type = NavType.StringType },
    navArgument(Routes.ARG_MEAL) { type = NavType.StringType },
)

@Composable
fun CaloreNavHost(
    navController: NavHostController = rememberNavController(),
    startViewModel: StartViewModel = viewModel(factory = AppViewModelProvider.Factory),
    openRoute: String? = null,
    onOpened: () -> Unit = {},
) {
    // The first screen depends on whether the setup has run; deciding takes a few milliseconds.
    val startRoute = startViewModel.startRoute.collectAsStateWithLifecycle().value ?: return
    val backStackEntry by navController.currentBackStackEntryAsState()

    // A tapped notification opens its screen on top of Today (not during the first-run setup).
    LaunchedEffect(openRoute, startRoute) {
        if (openRoute != null && startRoute == Routes.DIARY && openRoute in NOTIFICATION_ROUTES) {
            navController.navigate(openRoute) { launchSingleTop = true }
        }
        if (openRoute != null) onOpened()
    }
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
            startDestination = startRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    onDone = {
                        navController.navigate(Routes.DIARY) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    },
                )
            }
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
                    onScan = { date, meal -> navController.navigate(Routes.scanner(date, meal)) },
                )
            }
            composable(Routes.SCANNER, arguments = dateAndMealArgs) { entry ->
                val date = parseIsoDate(entry.arguments?.getString(Routes.ARG_DATE)) ?: java.time.LocalDate.now()
                val meal = MealType.entries.firstOrNull { it.name == entry.arguments?.getString(Routes.ARG_MEAL) } ?: MealType.SNACK
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onFound = { foodId ->
                        navController.navigate(Routes.foodDetail(foodId, date, meal)) {
                            popUpTo(Routes.SCANNER) { inclusive = true }
                        }
                    },
                    onAddYourself = { barcode ->
                        navController.navigate(Routes.customFood(date, meal, barcode = barcode)) {
                            popUpTo(Routes.SCANNER) { inclusive = true }
                        }
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
                    navArgument(Routes.ARG_BARCODE) {
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
            composable(Routes.ME) {
                MeScreen(
                    onOpen = { destination ->
                        val route = when (destination) {
                            MeDestination.PROFILE -> Routes.PROFILE
                            MeDestination.GOALS -> Routes.GOALS
                            MeDestination.BODY -> Routes.WEIGHT
                            MeDestination.SETTINGS -> Routes.SETTINGS
                            MeDestination.ABOUT -> Routes.ABOUT
                        }
                        navController.navigate(route)
                    },
                )
            }
            composable(Routes.WEIGHT) {
                WeightScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBodyFat = { navController.navigate(Routes.BODY_FAT) },
                    onOpenCheckin = { navController.navigate(Routes.CHECKIN) },
                )
            }
            composable(Routes.CHECKIN) { CheckinScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.BODY_FAT) {
                BodyFatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTutorial = { navController.navigate(Routes.BODY_FAT_TUTORIAL) },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.BODY_FAT_TUTORIAL) {
                BodyFatTutorialScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GOALS) {
                GoalsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCalculator = { navController.navigate(Routes.CALCULATOR) },
                )
            }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
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

/** Screens a notification may open. */
private val NOTIFICATION_ROUTES = setOf(Routes.BODY_FAT, Routes.CHECKIN)

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
