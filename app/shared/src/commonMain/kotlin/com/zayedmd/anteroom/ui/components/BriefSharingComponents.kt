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

/*
 * ClinicQrCard used to live here: a 🏁 emoji captioned "SCAN QR" beside a
 * "Copy link" button pointing at /s/{brief_id} on a domain that may not
 * resolve. Nothing generated a QR code and nothing served that page, and the
 * URL was guessable — a link to a stranger's medication list one increment
 * away. The public share page was cut, so the card and the claim went with it.
 */

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
