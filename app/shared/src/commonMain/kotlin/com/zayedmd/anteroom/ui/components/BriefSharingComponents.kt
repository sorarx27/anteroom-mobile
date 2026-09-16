package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun ClinicQrCard(
    shareUrlPath: String?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (shareUrlPath.isNullOrBlank()) return

    val fullUrl = "https://anteroom.app$shareUrlPath"

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AnteroomColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🏥", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share with your clinic",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan this code at reception to open the brief securely in a browser.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = AnteroomColors.Muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = { onOpenLink(fullUrl) },
                    shape = RoundedCornerShape(12.dp),
                    color = AnteroomColors.BrandSecondary.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔗", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy link",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AnteroomColors.BrandPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Simulated clean QR code block
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AnteroomColors.SurfaceSecondary,
                border = BorderStroke(1.dp, AnteroomColors.Border),
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🏁", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SCAN QR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnteroomColors.BrandPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BriefPdfFooter(
    isSubscribed: Boolean,
    onDownloadPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AnteroomColors.Surface,
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onDownloadPdf,
                colors = ButtonDefaults.buttonColors(containerColor = AnteroomColors.BrandPrimary),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "📥", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSubscribed) "Download doctor-ready PDF" else "Download PDF (watermarked)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (!isSubscribed) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Free preview includes watermark. Upgrade to Pro for clean export.",
                    fontSize = 11.sp,
                    color = AnteroomColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
