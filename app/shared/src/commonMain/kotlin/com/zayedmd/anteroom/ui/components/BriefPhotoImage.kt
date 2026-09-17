package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.zayedmd.anteroom.model.BriefPhoto
import com.zayedmd.anteroom.ui.theme.AnteroomColors

/**
 * Renders a page of a brief.
 *
 * Until now every one of these sites drew a 📄 emoji, and one of them captioned
 * it "High-resolution source rendering active for clinical cross-examination."
 * Nothing was rendering. Tapping a flagged item to check it against the
 * original is the entire verification story, so this has to be a real image.
 *
 * [BriefPhoto.url] is the Firebase Storage download URL, written by
 * `PhotoUploadService` once the bytes are actually in the bucket. It is blank
 * while an upload is in flight, which is a normal state rather than an error.
 */
@Composable
fun BriefPhotoImage(
    photo: BriefPhoto,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    glyphSize: TextUnit = 28.sp,
    backgroundColor: Color = Color(0xFFE9ECEF)
) {
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (photo.url.isBlank()) {
            PhotoStatus(
                glyph = "📄",
                glyphSize = glyphSize,
                label = "Uploading…"
            )
        } else {
            SubcomposeAsyncImage(
                model = photo.url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AnteroomColors.BrandPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                error = {
                    PhotoStatus(
                        glyph = "⚠️",
                        glyphSize = glyphSize,
                        label = "Couldn't load this page"
                    )
                }
            )
        }
    }
}

@Composable
private fun PhotoStatus(
    glyph: String,
    glyphSize: TextUnit,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(text = glyph, fontSize = glyphSize)
        Text(
            text = label,
            fontSize = 11.sp,
            color = AnteroomColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
