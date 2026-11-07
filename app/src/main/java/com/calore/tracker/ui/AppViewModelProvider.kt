package com.calore.tracker.ui

import com.calore.tracker.ui.plan.WeekPlanViewModel
import com.calore.tracker.ui.cook.CookModeViewModel
import com.calore.tracker.ui.cook.CookViewModel
import com.calore.tracker.ui.kitchen.KitchenViewModel
import com.calore.tracker.ui.kitchen.PeopleViewModel
import com.calore.tracker.ui.kitchen.PersonViewModel
import com.calore.tracker.ui.kitchen.PotsViewModel
import com.calore.tracker.ui.kitchen.WasteViewModel
import com.calore.tracker.ui.recipes.RecipeDetailViewModel
import com.calore.tracker.ui.recipes.RecipeEditViewModel
import com.calore.tracker.ui.recipes.RecipeListViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.map
import com.calore.tracker.AppContainer
import com.calore.tracker.CaloreApp
import com.calore.tracker.ui.addfood.AddFoodViewModel
import com.calore.tracker.ui.backup.BackupViewModel
import com.calore.tracker.ui.body.BodyFatViewModel
import com.calore.tracker.ui.calculator.CalculatorViewModel
import com.calore.tracker.ui.checkin.CheckinViewModel
import com.calore.tracker.ui.customfood.CustomFoodViewModel
import com.calore.tracker.ui.fooddetail.FoodDetailViewModel
import com.calore.tracker.ui.goals.GoalsViewModel
import com.calore.tracker.ui.profile.ProfileViewModel
import com.calore.tracker.ui.scanner.ScannerViewModel
import com.calore.tracker.ui.navigation.StartViewModel
import com.calore.tracker.ui.setup.SetupViewModel
import com.calore.tracker.ui.settings.SettingsViewModel
import com.calore.tracker.ui.today.TodayViewModel
import com.calore.tracker.ui.weight.WeightViewModel

/** Builds every ViewModel from the app-wide [AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            TodayViewModel(createSavedStateHandle(), c.diaryRepository, c.goalsRepository, c.clock, c.appPrefs, c.memberRepository)
        }
        initializer {
            val c = container()
            AddFoodViewModel(createSavedStateHandle(), c.foodRepository, c.clock, c.diaryRepository, c.recipeRepository)
        }
        initializer {
            val c = container()
            FoodDetailViewModel(createSavedStateHandle(), c.foodRepository, c.diaryRepository, c.clock)
        }
        initializer {
            val c = container()
            CustomFoodViewModel(createSavedStateHandle(), c.foodRepository, c.clock)
        }
        initializer {
            val c = container()
            GoalsViewModel(c.goalsRepository)
        }
        initializer {
            val c = container()
            CalculatorViewModel(c.memberRepository, c.weightRepository, c.clock, c.bodyRepository.latestBodyFat())
        }
        initializer {
            val c = container()
            WeightViewModel(
                c.weightRepository,
                c.clock,
                c.bodyRepository.measurements(),
                c.appPrefs.settings.map { it.hideNumbers },
                c.bodyRepository.checkins(),
            )
        }
        initializer {
            BackupViewModel(container().backupRepository)
        }
        initializer {
            val c = container()
            SettingsViewModel(c.appPrefs, c.reminderSettings, c.reminders)
        }
        initializer {
            val c = container()
            CheckinViewModel(c.checkinService)
        }
        initializer {
            val c = container()
            BodyFatViewModel(c.memberRepository, c.bodyRepository, c.reminderSettings, c.clock)
        }
        initializer {
            val c = container()
            ScannerViewModel(createSavedStateHandle(), c.foodRepository, c.clock)
        }
        initializer {
            val c = container()
            ProfileViewModel(c.memberRepository, c.weightRepository, c.clock, c.bodyRepository.latestBodyFat())
        }
        initializer {
            val c = container()
            StartViewModel(c.memberRepository, c.appPrefs)
        }
        initializer {
            val c = container()
            RecipeListViewModel(c.recipeRepository, c.memberRepository)
        }
        initializer {
            val c = container()
            RecipeDetailViewModel(createSavedStateHandle(), c.recipeRepository, c.memberRepository, c.foodRepository, c.diaryRepository, c.clock)
        }
        initializer {
            val c = container()
            RecipeEditViewModel(createSavedStateHandle(), c.recipeRepository, c.foodRepository)
        }
        initializer {
            PotsViewModel(container().cookingRepository)
        }
        initializer {
            PeopleViewModel(container().memberRepository)
        }
        initializer {
            val c = container()
            WeekPlanViewModel(c.planRepository, c.memberRepository, c.recipeRepository, c.appPrefs, c.clock)
        }
        initializer {
            val c = container()
            PersonViewModel(createSavedStateHandle(), c.memberRepository, c.clock)
        }
        initializer {
            val c = container()
            KitchenViewModel(c.cookingRepository, c.memberRepository, c.clock)
        }
        initializer {
            val c = container()
            WasteViewModel(c.cookingRepository, c.clock)
        }
        initializer {
            val c = container()
            CookViewModel(createSavedStateHandle(), c.recipeRepository, c.cookingRepository, c.memberRepository, c.foodRepository, c.clock)
        }
        initializer {
            val c = container()
            CookModeViewModel(createSavedStateHandle(), c.recipeRepository, c.reminders, c.appPrefs, c.clock)
        }
        initializer {
            val c = container()
            SetupViewModel(c.memberRepository, c.weightRepository, c.appPrefs, c.clock)
        }
    }

    // The manifest names CaloreApp as the Application class, so this cast always holds.
    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as CaloreApp).container
}
