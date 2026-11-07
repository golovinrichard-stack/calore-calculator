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
import androidx.navigation.NavGraphBuilder
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
import com.calore.tracker.ui.cook.CookModeScreen
import com.calore.tracker.ui.cook.CookScreen
import com.calore.tracker.ui.kitchen.KitchenScreen
import com.calore.tracker.ui.kitchen.PeopleScreen
import com.calore.tracker.ui.shop.ShopsScreen
import com.calore.tracker.ui.shop.ShoppingScreen
import com.calore.tracker.ui.shop.StatsScreen
import com.calore.tracker.ui.kitchen.PersonScreen
import com.calore.tracker.ui.kitchen.PotsScreen
import com.calore.tracker.ui.kitchen.WasteScreen
import com.calore.tracker.ui.me.MeDestination
import com.calore.tracker.ui.me.MeScreen
import com.calore.tracker.ui.profile.ProfileScreen
import com.calore.tracker.ui.recipes.PlanScreen
import com.calore.tracker.ui.plan.PlanWeekScreen
import com.calore.tracker.ui.kitchen.UseItUpScreen
import com.calore.tracker.ui.recipes.RecipeDetailScreen
import com.calore.tracker.ui.recipes.RecipeEditScreen
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

/** The bottom bar: Today · Plan · Me (M3). Kitchen and Shop join in M4–M5. */
/** The bottom bar: Today · Plan · Kitchen · Shop · Me. */
private enum class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(Routes.DIARY, R.string.nav_today, R.drawable.ic_nav_diary),
    PLAN(Routes.PLAN, R.string.nav_plan, R.drawable.ic_nav_plan),
    KITCHEN(Routes.KITCHEN, R.string.nav_kitchen, R.drawable.ic_nav_kitchen),
    SHOP(Routes.SHOP, R.string.nav_shop, R.drawable.ic_nav_shop),
    ME(Routes.ME, R.string.nav_me, R.drawable.ic_nav_me),
}

private val topLevelTabs = TopLevel.entries

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
    val showBottomBar = topLevelTabs.any { it.route == currentRoute }

    // Each screen has its own Scaffold for its top bar; this outer one only hosts the
    // bottom navigation, so it leaves window insets to the inner screens.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelTabs.forEach { destination ->
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
                TodayScreen(
                    onAddFood = { date, meal -> navController.navigate(Routes.addFood(date, meal)) },
                    onOpenRecipe = { id -> navController.navigate(Routes.recipe(id)) },
                )
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
                            MeDestination.PEOPLE -> Routes.PEOPLE
                            MeDestination.STATS -> Routes.STATS
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
                    onOpenPots = { navController.navigate(Routes.POTS) },
                    onOpenShops = { navController.navigate(Routes.SHOPS) },
                )
            }
            kitchenRoutes(navController)
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
            recipeRoutes(navController)
        }
    }
}

/** Cooking, boxes, pots and people (M4). */
private fun NavGraphBuilder.kitchenRoutes(navController: NavHostController) {
    composable(Routes.POTS) { PotsScreen(onBack = { navController.popBackStack() }) }
    composable(Routes.KITCHEN) { entry ->
        val scanned by entry.savedStateHandle.getStateFlow<Long?>(Routes.RESULT_PICKED_FOOD_ID, null).collectAsStateWithLifecycle()
        KitchenScreen(
            onOpenWaste = { navController.navigate(Routes.WASTE) },
            onScanForPantry = { navController.navigate(Routes.SCANNER_PICK) },
            onUseItUp = { navController.navigate(Routes.USE_IT_UP) },
            scannedFoodId = scanned,
            onScannedFoodUsed = { entry.savedStateHandle[Routes.RESULT_PICKED_FOOD_ID] = null },
        )
    }
    composable(Routes.WASTE) { WasteScreen(onBack = { navController.popBackStack() }) }
    composable(Routes.USE_IT_UP) {
        UseItUpScreen(onBack = { navController.popBackStack() }, onOpenRecipe = { id -> navController.navigate(Routes.recipe(id)) })
    }
    composable(Routes.SHOPS) { ShopsScreen(onBack = { navController.popBackStack() }) }
    composable(Routes.STATS) { StatsScreen(onBack = { navController.popBackStack() }) }
    composable(Routes.SHOP) { entry ->
        val scanned by entry.savedStateHandle.getStateFlow<Long?>(Routes.RESULT_PICKED_FOOD_ID, null).collectAsStateWithLifecycle()
        ShoppingScreen(
            onScanProduct = { navController.navigate(Routes.SCANNER_PICK) },
            onOpenShops = { navController.navigate(Routes.SHOPS) },
            scannedFoodId = scanned,
            onScannedFoodUsed = { entry.savedStateHandle[Routes.RESULT_PICKED_FOOD_ID] = null },
        )
    }
    composable(Routes.PEOPLE) {
        PeopleScreen(onBack = { navController.popBackStack() }, onOpen = { id -> navController.navigate(Routes.person(id)) })
    }
    composable(
        Routes.PERSON,
        arguments = listOf(
            navArgument(Routes.ARG_MEMBER_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { PersonScreen(onBack = { navController.popBackStack() }) }
    composable(
        Routes.COOK,
        arguments = listOf(
            navArgument(Routes.ARG_RECIPE_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(Routes.ARG_PLAN_DATE) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        CookScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.navigateToTopLevel(Routes.KITCHEN) },
            onOpenCookMode = { ids -> navController.navigate(Routes.cookMode(ids)) },
        )
    }
    composable(Routes.COOK_MODE, arguments = listOf(navArgument(Routes.ARG_RECIPE_IDS) { type = NavType.StringType })) {
        CookModeScreen(onBack = { navController.popBackStack() }, onFinished = { navController.popBackStack() })
    }
}

/** The recipe book (M3): list, detail, editor, and picking a food for an ingredient. */
private fun NavGraphBuilder.recipeRoutes(navController: NavHostController) {
    val recipeIdArg = navArgument(Routes.ARG_RECIPE_ID) { type = NavType.StringType }
    composable(Routes.PLAN) {
        PlanScreen(
            onOpenRecipe = { id -> navController.navigate(Routes.recipe(id)) },
            onNewRecipe = { navController.navigate(Routes.recipeEdit()) },
            onCookFromPlan = { date -> navController.navigate(Routes.cookFromPlan(date)) },
            onPlanWeek = { weekStart -> navController.navigate(Routes.planWeek(weekStart)) },
        )
    }
    composable(Routes.PLAN_WEEK, arguments = listOf(navArgument(Routes.ARG_WEEK_START) { type = NavType.StringType })) {
        PlanWeekScreen(
            onBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() },
            onOpenProfile = { navController.navigate(Routes.PROFILE) },
        )
    }
    composable(Routes.RECIPE, arguments = listOf(recipeIdArg)) {
        RecipeDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(Routes.recipeEdit(id)) },
            onDeleted = { navController.popBackStack() },
            onOpenRecipe = { id -> navController.navigate(Routes.recipe(id)) },
            onCook = { id -> navController.navigate(Routes.cook(id)) },
        )
    }
    composable(
        Routes.RECIPE_EDIT,
        arguments = listOf(
            navArgument(Routes.ARG_RECIPE_ID) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { entry ->
        val picked by entry.savedStateHandle.getStateFlow<Long?>(Routes.RESULT_PICKED_FOOD_ID, null).collectAsStateWithLifecycle()
        RecipeEditScreen(
            onBack = { navController.popBackStack() },
            onSaved = { id, isNew ->
                if (isNew) {
                    navController.navigate(Routes.recipe(id)) { popUpTo(Routes.RECIPE_EDIT) { inclusive = true } }
                } else {
                    navController.popBackStack()
                }
            },
            onScanIngredient = { navController.navigate(Routes.SCANNER_PICK) },
            onCreateFood = { name -> navController.navigate(Routes.customFoodPick(name = name)) },
            pickedFoodId = picked,
            onPickedFoodUsed = { entry.savedStateHandle[Routes.RESULT_PICKED_FOOD_ID] = null },
        )
    }
    composable(Routes.SCANNER_PICK) {
        ScannerScreen(
            onBack = { navController.popBackStack() },
            onFound = { foodId -> navController.returnPickedFood(foodId) },
            onAddYourself = { barcode ->
                navController.navigate(Routes.customFoodPick(barcode = barcode)) {
                    popUpTo(Routes.SCANNER_PICK) { inclusive = true }
                }
            },
        )
    }
    composable(
        Routes.CUSTOM_FOOD_PICK,
        arguments = listOf(
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
            onCreated = { foodId, _, _ -> navController.returnPickedFood(foodId) },
            onUpdated = { navController.popBackStack() },
        )
    }
}

/** Hands a chosen food to the screen below (the recipe editor) and goes back to it. */
private fun NavHostController.returnPickedFood(foodId: Long) {
    previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_PICKED_FOOD_ID, foodId)
    popBackStack()
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
