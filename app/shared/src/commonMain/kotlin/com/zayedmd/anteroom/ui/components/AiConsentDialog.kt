package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.ui.LegalLinks
import com.zayedmd.anteroom.ui.theme.AnteroomColors

/**
 * Asks before any page goes to Gemini. See [com.zayedmd.anteroom.data.AiConsent].
 *
 * Guideline 5.1.2(i) wants three things said plainly: what is sent, who it
 * goes to, and that it is an AI. Each gets its own paragraph so none of them
 * is buried, and the provider is named rather than called "a partner".
 *
 * Not dismissable by tapping outside: an accidental tap should not count as
 * an answer either way.
 */
@Composable
fun AiConsentDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        containerColor = AnteroomColors.Surface,
        title = {
            Text(
                text = "Anteroom uses AI to read your documents",
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.OnSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConsentParagraph(
                    "To turn your photos into a brief, Anteroom sends the pages you add to " +
                        "Gemini, an AI service from Google, running on Google Cloud Vertex AI " +
                        "in the EU."
                )
                ConsentParagraph(
                    "What is sent: the photos of your documents and the text read from them. " +
                        "That can include names, dates of birth, medications and test results."
                )
                ConsentParagraph(
                    "Gemini uses them only to identify the document, write your brief and " +
                        "translate it when you ask. Google processes them on Anteroom's behalf " +
                        "and does not use them to train its models."
                )
                ConsentParagraph(
                    "Nothing is sent until you agree. You can withdraw this at any time from " +
                        "your account, behind the avatar on the home screen."
                )
                Text(
                    text = "Read the Privacy Policy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.BrandPrimary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri(LegalLinks.PRIVACY) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(
                    text = "Agree and continue",
                    color = AnteroomColors.BrandPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Not now", color = AnteroomColors.OnSurfaceSecondary)
            }
        }
    )
}

@Composable
private fun ConsentParagraph(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = AnteroomColors.OnSurface
    )
}
