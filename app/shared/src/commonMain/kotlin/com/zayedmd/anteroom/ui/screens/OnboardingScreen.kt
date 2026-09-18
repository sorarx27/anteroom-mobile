package com.zayedmd.anteroom.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anteroom.app.shared.generated.resources.Res
import anteroom.app.shared.generated.resources.logo
import anteroom.app.shared.generated.resources.onboardingbg
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import org.jetbrains.compose.resources.painterResource

data class OnboardingSlide(
    val title: String,
    val subtitle: String
)

private val SLIDES = listOf(
    OnboardingSlide(
        title = "Snap the paperwork.",
        subtitle = "Referral letters, med lists, lab photos — Anteroom reads them and pulls out what matters."
    ),
    OnboardingSlide(
        title = "Walk in doctor-ready.",
        subtitle = "One clean page. Flags unreadable high-risk doses instead of guessing. Export a PDF to hand your doctor."
    )
)

@Composable
fun OnboardingScreen(
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val slide = SLIDES[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1713))
    ) {
        // If on the first onboarding slide, show onboardingbg as background
        if (currentIndex == 0) {
            Image(
                painter = painterResource(Res.drawable.onboardingbg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Ambient dark gradient overlay to ensure text contrast and cohesive style
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (currentIndex == 0) {
                            listOf(
                                Color(0x770F1713),
                                Color(0x550F1713),
                                Color(0xDD0C1310),
                                Color(0xF80C1310)
                            )
                        } else {
                            listOf(
                                Color(0xFF0F1713),
                                Color(0xFF172820),
                                Color(0xFF0C1310)
                            )
                        }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Navigation & Logo Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (currentIndex > 0) {
                                    currentIndex--
                                } else {
                                    onBack()
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "‹",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Logo at top left
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = "Anteroom Logo",
                        modifier = Modifier
                            .size(36.dp)
                    )
                }
            }

            // Slide Content
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) { index ->
                val current = SLIDES[index]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = current.title,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 42.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = current.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Pagination Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SLIDES.indices.forEach { i ->
                        val isSelected = i == currentIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Next / Create Account Button
                Button(
                    onClick = {
                        if (currentIndex < SLIDES.size - 1) {
                            currentIndex++
                        } else {
                            onCreateAccount()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AnteroomColors.BrandPrimary
                    )
                ) {
                    Text(
                        text = if (currentIndex < SLIDES.size - 1) "Next" else "Create account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val signinText = buildAnnotatedString {
                    append("Already have an account? ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append("Sign in")
                    }
                }

                Text(
                    text = signinText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onSignIn() }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
