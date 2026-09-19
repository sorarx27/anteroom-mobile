package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.ui.LegalLinks
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import com.zayedmd.anteroom.ui.userMessage
import kotlinx.coroutines.launch

/**
 * Account actions, reached from the avatar in the dashboard header.
 *
 * This exists because of two problems that were easy to miss by reading code.
 *
 * The avatar used to sign you out on a single tap, with no confirmation — the
 * least reversible thing on the screen behind its least deliberate gesture.
 *
 * And account deletion, which App Store Review Guideline 5.1.1(v) requires to
 * be inside the app, was first written into `MainHomeScreen` — a composable
 * nothing renders. It compiled, it looked right in review, and it would have
 * shipped a build whose review notes pointed at a button that does not exist.
 * Account actions live here now, on the one screen that is actually reachable
 * after sign-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    user: AppUser,
    authService: AuthService,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!deleting) onDismiss() },
        containerColor = AnteroomColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = user.name?.takeIf { it.isNotBlank() } ?: "Your account",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.OnSurface
            )
            Text(
                text = user.email,
                fontSize = 13.sp,
                color = AnteroomColors.OnSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            AccountSheetRow("Privacy Policy") { uriHandler.openUri(LegalLinks.PRIVACY) }
            HorizontalDivider(color = AnteroomColors.Border)
            AccountSheetRow("Terms of Use") { uriHandler.openUri(LegalLinks.TERMS) }
            HorizontalDivider(color = AnteroomColors.Border)
            AccountSheetRow("Support") { uriHandler.openUri(LegalLinks.SUPPORT) }
            HorizontalDivider(color = AnteroomColors.Border)
            AccountSheetRow("Sign out") {
                scope.launch { authService.signOut() }
            }
            HorizontalDivider(color = AnteroomColors.Border)
            AccountSheetRow("Delete account", tint = AnteroomColors.Error) {
                error = null
                confirmingDelete = true
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error!!,
                    fontSize = 12.sp,
                    color = AnteroomColors.Error
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmingDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This erases your account, every brief, every page you photographed, " +
                        "and your family profiles. It cannot be undone, and support cannot " +
                        "recover it.\n\n" +
                        "An active subscription is billed by the App Store or Google Play, " +
                        "not by Anteroom. Cancel it in your store account — deleting here " +
                        "does not stop it."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        error = null
                        scope.launch {
                            try {
                                authService.deleteAccount()
                                // Nothing to reset: deleteAccount signs out, and
                                // the whole screen leaves the tree behind it.
                            } catch (e: Throwable) {
                                error = e.userMessage()
                                deleting = false
                                confirmingDelete = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (deleting) "Deleting…" else "Delete permanently",
                        color = AnteroomColors.Error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmingDelete = false }) {
                    Text("Keep my account")
                }
            }
        )
    }
}

@Composable
private fun AccountSheetRow(
    label: String,
    tint: androidx.compose.ui.graphics.Color = AnteroomColors.OnSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}
