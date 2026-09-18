package com.zayedmd.anteroom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zayedmd.anteroom.media.CapturedPhoto
import com.zayedmd.anteroom.ui.theme.AnteroomColors

/**
 * The pages captured so far, while still on the capture screen.
 *
 * Rendered from the in-memory bytes rather than the Storage download URL:
 * those bytes are already in hand the instant the picker returns, so the page
 * appears immediately instead of after a round trip. Until this existed the
 * screen only said "2 pages captured" and you had to leave it to find out
 * whether you had photographed the right thing.
 */
@Composable
fun CaptureThumbnailStrip(
    photos: List<CapturedPhoto>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Keep the newest page in view; a strip that silently grows off-screen is
    // worse than no strip when you are checking you got all the pages.
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    AnimatedVisibility(
        visible = photos.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text(
                text = "CAPTURED (${photos.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = AnteroomColors.Muted,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                photos.forEachIndexed { index, photo ->
                    CapturedPageThumbnail(photo = photo, pageNumber = index + 1)
                }
            }
        }
    }
}

@Composable
private fun CapturedPageThumbnail(photo: CapturedPhoto, pageNumber: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFE9ECEF),
        border = BorderStroke(1.dp, AnteroomColors.Border),
        modifier = Modifier.width(76.dp).height(100.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val bytes = photo.bytes
            if (bytes != null && bytes.isNotEmpty()) {
                // Coil decodes a ByteArray directly via its built-in
                // ByteArrayFetcher, so nothing has to be written to disk to
                // preview it.
                AsyncImage(
                    model = bytes,
                    contentDescription = "Captured page $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📄", fontSize = 24.sp)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$pageNumber",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
