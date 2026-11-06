package com.wanderlog.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wanderlog.android.presentation.ai.ask.AskTripScreen
import com.wanderlog.android.presentation.ai.generate.AiGenerateScreen
import com.wanderlog.android.presentation.attachments.AttachmentViewerScreen
import com.wanderlog.android.presentation.attachments.AttachmentsScreen
import com.wanderlog.android.presentation.budget.BudgetScreen
import com.wanderlog.android.presentation.itinerary.attachments.ItemAttachmentsScreen
import com.wanderlog.android.presentation.map.MapScreen
import com.wanderlog.android.presentation.itinerary.TripItineraryScreen
import com.wanderlog.android.presentation.notes.TripNotesScreen
import com.wanderlog.android.presentation.packing.PackingScreen
import com.wanderlog.android.presentation.settings.AiOnboardingDialog
import com.wanderlog.android.presentation.settings.SettingsScreen
import com.wanderlog.android.presentation.settings.SettingsViewModel
import com.wanderlog.android.presentation.share.ShareImportScreen
import com.wanderlog.android.presentation.sync.TripSyncScreen
import com.wanderlog.android.presentation.tripPhotos.TripPhotosScreen
import com.wanderlog.android.presentation.trips.form.TripFormScreen
import com.wanderlog.android.presentation.trips.list.TripListScreen

@Composable
fun WanderlogNavGraph(startAtShare: Boolean = false) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val shouldPromptForOpenAiKey = rememberSaveable {
        !SettingsViewModel.hasConfiguredAiKey(context)
    }
    var showOpenAiOnboarding by rememberSaveable(shouldPromptForOpenAiKey) {
        mutableStateOf(shouldPromptForOpenAiKey)
    }

    val start = if (startAtShare) Screen.ShareImport.route else Screen.TripList.route

    NavHost(navController = navController, startDestination = start) {

        composable(Screen.TripList.route) {
            TripListScreen(
                onCreateTrip = { navController.navigate(Screen.TripForm.createRoute()) },
                onEditTrip = { tripId -> navController.navigate(Screen.TripForm.createRoute(tripId)) },
                onOpenTrip = { tripId -> navController.navigate(Screen.Itinerary.createRoute(tripId)) },
                onOpenNotes = { navController.navigate(Screen.Notes.createRoute()) },
                onOpenSync = { navController.navigate(Screen.TripSync.createRoute()) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.TripForm.route,
            arguments = listOf(navArgument(Screen.TripForm.ARG_TRIP_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            TripFormScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Itinerary.route,
            arguments = listOf(navArgument(Screen.Itinerary.ARG_TRIP_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString(Screen.Itinerary.ARG_TRIP_ID)!!
            TripItineraryScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() },
                onOpenMap = { dayId -> navController.navigate(Screen.Map.createRoute(tripId, dayId)) },
                onOpenBudget = { navController.navigate(Screen.Budget.createRoute(tripId)) },
                onOpenPacking = { navController.navigate(Screen.Packing.createRoute(tripId)) },
                onOpenNotes = { navController.navigate(Screen.Notes.createRoute(tripId)) },
                onOpenAiGenerate = { navController.navigate(Screen.AiGenerate.createRoute(tripId)) },
                onOpenAskTrip = { navController.navigate(Screen.AskTrip.createRoute(tripId)) },
                onOpenPhotos = { navController.navigate(Screen.TripPhotos.createRoute(tripId)) },
                onOpenItemAttachments = { itemId -> navController.navigate(Screen.ItemAttachments.createRoute(tripId, itemId)) },
                onOpenSync = { navController.navigate(Screen.TripSync.createRoute(tripId)) },
                onOpenAttachments = { pickOnOpen -> navController.navigate(Screen.Attachments.createRoute(tripId, pickOnOpen)) },
            )
        }

        composable(
            route = Screen.TripPhotos.route,
            arguments = listOf(navArgument(Screen.TripPhotos.ARG_TRIP_ID) { type = NavType.StringType })
        ) {
            TripPhotosScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Map.route,
            arguments = listOf(
                navArgument(Screen.Map.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Screen.Map.ARG_DAY_ID) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                }
            )
        ) {
            MapScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Budget.route,
            arguments = listOf(navArgument(Screen.Budget.ARG_TRIP_ID) { type = NavType.StringType })
        ) {
            BudgetScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Packing.route,
            arguments = listOf(navArgument(Screen.Packing.ARG_TRIP_ID) { type = NavType.StringType })
        ) {
            PackingScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AiGenerate.route,
            arguments = listOf(navArgument(Screen.AiGenerate.ARG_TRIP_ID) { type = NavType.StringType })
        ) {
            AiGenerateScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AskTrip.route,
            arguments = listOf(navArgument(Screen.AskTrip.ARG_TRIP_ID) { type = NavType.StringType })
        ) {
            AskTripScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.TripSync.route,
            arguments = listOf(navArgument(Screen.TripSync.ARG_TRIP_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            TripSyncScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Notes.route,
            arguments = listOf(navArgument(Screen.Notes.ARG_TRIP_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            TripNotesScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Attachments.route,
            arguments = listOf(
                navArgument(Screen.Attachments.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Screen.Attachments.ARG_PICK_ON_OPEN) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            AttachmentsScreen(
                onBack = { navController.popBackStack() },
                onOpenAttachment = { id -> navController.navigate(Screen.AttachmentViewer.createRoute(id)) },
                pickOnOpen = backStackEntry.arguments?.getBoolean(Screen.Attachments.ARG_PICK_ON_OPEN) == true
            )
        }

        composable(
            route = Screen.AttachmentViewer.route,
            arguments = listOf(navArgument(Screen.AttachmentViewer.ARG_ATTACHMENT_ID) { type = NavType.StringType })
        ) {
            AttachmentViewerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ItemAttachments.route,
            arguments = listOf(
                navArgument(Screen.ItemAttachments.ARG_TRIP_ID) { type = NavType.StringType },
                navArgument(Screen.ItemAttachments.ARG_ITEM_ID) { type = NavType.StringType }
            )
        ) {
            ItemAttachmentsScreen(
                onBack = { navController.popBackStack() },
                onOpenAttachment = { attachmentId ->
                    navController.navigate(Screen.AttachmentViewer.createRoute(attachmentId))
                }
            )
        }

        composable(Screen.ShareImport.route) {
            ShareImportScreen(
                onDone = { tripId ->
                    navController.navigate(Screen.Itinerary.createRoute(tripId)) {
                        popUpTo(Screen.ShareImport.route) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.navigate(Screen.TripList.route) {
                        popUpTo(Screen.ShareImport.route) { inclusive = true }
                    }
                }
            )
        }
    }

    if (showOpenAiOnboarding) {
        AiOnboardingDialog(
            onGoToSettings = {
                showOpenAiOnboarding = false
                navController.navigate(Screen.Settings.route)
            },
            onDismiss = { showOpenAiOnboarding = false }
        )
    }
}
