package com.zayedmd.anteroom.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.model.BriefLanguage
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun BriefGenerationProcessingDialog(
    isTranslating: Boolean = false,
    outputLanguage: BriefLanguage = BriefLanguage.en
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    var stepIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        stepIndex = 1
        kotlinx.coroutines.delay(1000)
        stepIndex = 2
    }

    val steps = if (isTranslating) {
        listOf(
            "Analyzing handwriting, labels & dosages…",
            "Translating clinical findings to ${if (outputLanguage == BriefLanguage.es) "Spanish" else "English"}…",
            "Synthesizing doctor-ready brief…"
        )
    } else {
        listOf(
            "Analyzing handwriting, labels & dosages…",
            "Extracting medications & lab values…",
            "Synthesizing doctor-ready brief…"
        )
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AnteroomColors.Surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.BrandSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✨",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Generating Clinical Brief",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.OnSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = steps.getOrElse(stepIndex) { steps.last() },
                    fontSize = 13.sp,
                    color = AnteroomColors.BrandPrimary.copy(alpha = pulseAlpha),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AnteroomColors.BrandPrimary,
                    trackColor = AnteroomColors.Border
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This usually takes 5-10 seconds",
                    fontSize = 11.sp,
                    color = AnteroomColors.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
