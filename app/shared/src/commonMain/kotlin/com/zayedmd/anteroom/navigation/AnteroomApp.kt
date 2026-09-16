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
import com.zayedmd.anteroom.ui.screens.*
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import com.zayedmd.anteroom.ui.theme.AnteroomTheme

sealed interface OnboardingDestination {
    object Welcome : OnboardingDestination
    object Onboarding : OnboardingDestination
    data class Auth(val mode: AuthMode) : OnboardingDestination
}

@Composable
fun AnteroomApp(
    authService: AuthService = remember { AuthServiceImpl() }
) {
    val status by authService.status.collectAsState()
    val user by authService.user.collectAsState()

    var onboardingDestination by remember { mutableStateOf<OnboardingDestination>(OnboardingDestination.Welcome) }

    AnteroomTheme {
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
                    MainHomeScreen(
                        user = currentUser,
                        authService = authService
                    )
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
    }
}
