package com.zayedmd.anteroom.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.auth.AuthServiceImpl
import com.zayedmd.anteroom.auth.AuthStatus
import com.zayedmd.anteroom.data.BriefsRepository
import com.zayedmd.anteroom.data.BriefsRepositoryImpl
import com.zayedmd.anteroom.data.PhotoUploadService
import com.zayedmd.anteroom.data.ProfilesRepository
import com.zayedmd.anteroom.data.ProfilesRepositoryImpl
import com.zayedmd.anteroom.export.rememberPdfSharer
import com.zayedmd.anteroom.media.CapturedPhoto
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.subscription.AnteroomPurchases
import com.zayedmd.anteroom.subscription.RevenueCatService
import com.zayedmd.anteroom.subscription.SubscriptionService
import com.zayedmd.anteroom.subscription.defaultRevenueCatApiKey
import com.zayedmd.anteroom.ui.AnteroomErrors
import com.zayedmd.anteroom.ui.ConfigureImageLoading
import com.zayedmd.anteroom.ui.launchSafely
import com.zayedmd.anteroom.ui.components.ErrorBanner
import com.zayedmd.anteroom.ui.components.PaywallModal
import com.zayedmd.anteroom.ui.screens.*
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import com.zayedmd.anteroom.ui.theme.AnteroomTheme
import kotlinx.coroutines.launch

sealed interface OnboardingDestination {
    object Welcome : OnboardingDestination
    object Onboarding : OnboardingDestination
    data class Auth(val mode: AuthMode) : OnboardingDestination
}

sealed interface AppScreen {
    object Dashboard : AppScreen
    data class ProfileAddEdit(val profile: Profile? = null) : AppScreen
    data class Capture(val profile: Profile?, val existingBriefId: String? = null) : AppScreen
    data class BriefDraft(val briefId: String) : AppScreen
    data class BriefView(val briefId: String) : AppScreen
}

@Composable
fun AnteroomApp(
    authService: AuthService = remember { AuthServiceImpl() },
    profilesRepository: ProfilesRepository = remember { ProfilesRepositoryImpl() },
    briefsRepository: BriefsRepository = remember { BriefsRepositoryImpl() },
    revenueCatService: RevenueCatService = AnteroomPurchases.service
) {
    ConfigureImageLoading()

    val status by authService.status.collectAsState()
    val user by authService.user.collectAsState()
    val isSubscribed by SubscriptionService.isSubscribed.collectAsState()

    var onboardingDestination by remember { mutableStateOf<OnboardingDestination>(OnboardingDestination.Welcome) }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Dashboard) }
    var showPaywallModal by remember { mutableStateOf(false) }
    var paywallTrigger by remember { mutableStateOf("family-profiles") }

    val pdfSharer = rememberPdfSharer()
    val photoUploadService = remember { PhotoUploadService(briefsRepository) }
    val uploadingCount by photoUploadService.uploadingCount.collectAsState()
    val uploadError by photoUploadService.lastError.collectAsState()
    var activeDraftPhotos by remember { mutableStateOf<List<CapturedPhoto>>(emptyList()) }
    var activeBriefId by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Configure RevenueCat once, then re-key the purchase identity whenever the signed-in user
    // changes so entitlements follow the Anteroom account rather than the device.
    LaunchedEffect(user?.user_id) {
        revenueCatService.initialize(defaultRevenueCatApiKey(), user?.user_id)
        revenueCatService.refreshCustomerInfo()
        revenueCatService.fetchOfferings()
    }

    AnteroomTheme {
        val errorMessage by AnteroomErrors.message.collectAsState()

        // Overlaid on everything rather than added screen by screen. The
        // repositories no longer fall back to mock data, so any screen can
        // now surface a real failure and all of them need somewhere to put it.
        Box(modifier = Modifier.fillMaxSize()) {
            when (status) {
                AuthStatus.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AnteroomColors.SurfaceInverse),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "ANTEROOM",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            )
                            CircularProgressIndicator(
                                color = AnteroomColors.Brand,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                AuthStatus.Unauthenticated -> {
                    AnimatedContent(
                        targetState = onboardingDestination,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { destination ->
                        when (destination) {
                            is OnboardingDestination.Welcome -> {
                                WelcomeScreen(
                                    onGetStarted = {
                                        onboardingDestination = OnboardingDestination.Onboarding
                                    },
                                    onSignIn = {
                                        onboardingDestination = OnboardingDestination.Auth(AuthMode.SignIn)
                                    }
                                )
                            }
                            is OnboardingDestination.Onboarding -> {
                                OnboardingScreen(
                                    onBack = {
                                        onboardingDestination = OnboardingDestination.Welcome
                                    },
                                    onCreateAccount = {
                                        onboardingDestination = OnboardingDestination.Auth(AuthMode.SignUp)
                                    },
                                    onSignIn = {
                                        onboardingDestination = OnboardingDestination.Auth(AuthMode.SignIn)
                                    }
                                )
                            }
                            is OnboardingDestination.Auth -> {
                                AuthScreen(
                                    initialMode = destination.mode,
                                    onBack = {
                                        onboardingDestination = OnboardingDestination.Welcome
                                    },
                                    authService = authService
                                )
                            }
                        }
                    }
                }

                AuthStatus.Authenticated -> {
                    val currentUser = user
                    if (currentUser != null && !currentUser.profile_completed) {
                        ProfileSetupScreen(
                            user = currentUser,
                            authService = authService
                        )
                    } else if (currentUser != null) {
                        when (val screen = currentScreen) {
                            is AppScreen.Dashboard -> {
                                DashboardScreen(
                                    user = currentUser,
                                    authService = authService,
                                    profilesRepository = profilesRepository,
                                    briefsRepository = briefsRepository,
                                    isSubscribed = isSubscribed,
                                    onUpgradeClick = {
                                        paywallTrigger = "general"
                                        showPaywallModal = true
                                    },
                                    onAddProfileClick = {
                                        if (!isSubscribed) {
                                            paywallTrigger = "family-profiles"
                                            showPaywallModal = true
                                        } else {
                                            currentScreen = AppScreen.ProfileAddEdit(null)
                                        }
                                    },
                                    onEditProfileClick = { profileToEdit ->
                                        currentScreen = AppScreen.ProfileAddEdit(profileToEdit)
                                    },
                                    onSnapClick = {
                                        scope.launchSafely {
                                            // ensureSelfProfile rather than a lookup with a
                                            // "default" fallback: the Firestore rule for creating
                                            // a brief checks the referenced profile document
                                            // actually exists, so an invented id was a guaranteed
                                            // PERMISSION_DENIED. Idempotent, so this costs one read.
                                            val active = profilesRepository.ensureSelfProfile(
                                                userId = currentUser.user_id,
                                                displayName = currentUser.name ?: ""
                                            )
                                            val draft = briefsRepository.createBrief(
                                                userId = currentUser.user_id,
                                                profileId = active.profile_id
                                            )
                                            activeBriefId = draft.brief_id
                                            activeDraftPhotos = emptyList()
                                            currentScreen = AppScreen.Capture(active, draft.brief_id)
                                        }
                                    },
                                    onBriefClick = { brief ->
                                        if (brief.status == com.zayedmd.anteroom.model.BriefStatus.draft) {
                                            activeBriefId = brief.brief_id
                                            currentScreen = AppScreen.BriefDraft(brief.brief_id)
                                        } else {
                                            currentScreen = AppScreen.BriefView(brief.brief_id)
                                        }
                                    }
                                )
                            }

                            is AppScreen.Capture -> {
                                CaptureScreen(
                                    activeProfile = screen.profile,
                                    briefId = screen.existingBriefId ?: activeBriefId,
                                    photos = activeDraftPhotos,
                                    uploadingCount = uploadingCount,
                                    errorMessage = uploadError,
                                    onPhotosCaptured = { newPhotos ->
                                        activeDraftPhotos = activeDraftPhotos + newPhotos
                                        val targetBriefId = screen.existingBriefId ?: activeBriefId
                                        if (targetBriefId != null) {
                                            scope.launchSafely {
                                                photoUploadService.uploadPhotos(targetBriefId, newPhotos)
                                            }
                                        }
                                    },
                                    onCancel = {
                                        photoUploadService.clearError()
                                        currentScreen = AppScreen.Dashboard
                                    },
                                    onReviewClick = {
                                        val targetBriefId = screen.existingBriefId ?: activeBriefId
                                        if (targetBriefId != null) {
                                            currentScreen = AppScreen.BriefDraft(targetBriefId)
                                        } else {
                                            currentScreen = AppScreen.Dashboard
                                        }
                                    }
                                )
                            }

                            is AppScreen.BriefDraft -> {
                                BriefDraftScreen(
                                    briefId = screen.briefId,
                                    briefsRepository = briefsRepository,
                                    isSubscribed = isSubscribed,
                                    onBack = {
                                        currentScreen = AppScreen.Dashboard
                                    },
                                    onAddMorePhotos = {
                                        currentScreen = AppScreen.Capture(null, screen.briefId)
                                    },
                                    onBriefGenerated = { completedBrief ->
                                        currentScreen = AppScreen.BriefView(completedBrief.brief_id)
                                    },
                                    onDiscardDraft = {
                                        currentScreen = AppScreen.Dashboard
                                    },
                                    onUpgradeRequired = {
                                        paywallTrigger = "translate"
                                        showPaywallModal = true
                                    }
                                )
                            }

                            is AppScreen.BriefView -> {
                                BriefViewScreen(
                                    briefId = screen.briefId,
                                    briefsRepository = briefsRepository,
                                    isSubscribed = isSubscribed,
                                    onBack = {
                                        currentScreen = AppScreen.Dashboard
                                    },
                                    onEditDraft = {
                                        currentScreen = AppScreen.BriefDraft(screen.briefId)
                                    },
                                    onUpgradeRequired = {
                                        paywallTrigger = "brief-view"
                                        showPaywallModal = true
                                    },
                                    onOpenShareLink = { fullUrl ->
                                        // Fallback/log URL or open in browser
                                    },
                                    onDownloadPdf = {
                                        // The export runs for everyone. The
                                        // server decides whether it comes back
                                        // watermarked, so a free user still
                                        // gets a usable brief and sees exactly
                                        // what Pro removes -- which sells the
                                        // upgrade better than a locked button.
                                        scope.launchSafely {
                                            val pdf = briefsRepository.renderBriefPdf(screen.briefId)
                                            pdfSharer.share(pdf)
                                            if (pdf.watermarked) {
                                                paywallTrigger = "clean-export"
                                                showPaywallModal = true
                                            }
                                        }
                                    }
                                )
                            }

                            is AppScreen.ProfileAddEdit -> {
                                ProfileAddEditScreen(
                                    editingProfile = screen.profile,
                                    isSubscribed = isSubscribed,
                                    onBack = {
                                        currentScreen = AppScreen.Dashboard
                                    },
                                    onSaveProfile = { name, relationship, dob, sex ->
                                        scope.launchSafely {
                                            if (screen.profile != null) {
                                                profilesRepository.updateProfile(
                                                    profileId = screen.profile.profile_id,
                                                    name = name,
                                                    relationship = relationship,
                                                    dob = dob,
                                                    sex = sex
                                                )
                                            } else {
                                                profilesRepository.createProfile(
                                                    userId = currentUser.user_id,
                                                    name = name,
                                                    relationship = relationship,
                                                    dob = dob,
                                                    sex = sex
                                                )
                                            }
                                            currentScreen = AppScreen.Dashboard
                                        }
                                    },
                                    onDeleteProfile = { profileIdToDelete ->
                                        scope.launchSafely {
                                            profilesRepository.deleteProfile(profileIdToDelete)
                                            currentScreen = AppScreen.Dashboard
                                        }
                                    },
                                    onUpgradeRequired = {
                                        paywallTrigger = "family-profiles"
                                        showPaywallModal = true
                                    }
                                )
                            }
                        }

                        if (showPaywallModal) {
                            PaywallModal(
                                triggerReason = paywallTrigger,
                                revenueCatService = revenueCatService,
                                onDismiss = { showPaywallModal = false },
                                onUpgradeSuccess = {
                                    // RevenueCat is the source of truth here: the service already
                                    // pushed the verified anteroom_pro entitlement into
                                    // SubscriptionService, so the modal only has to get out of the way.
                                    showPaywallModal = false
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AnteroomColors.Surface),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AnteroomColors.BrandPrimary)
                        }
                    }
                }
            }

            ErrorBanner(
                message = errorMessage,
                onDismiss = AnteroomErrors::clear,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
