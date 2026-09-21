package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import com.zayedmd.anteroom.ui.userMessage
import kotlinx.coroutines.launch

enum class AuthMode {
    SignIn, SignUp
}

/**
 * Turns a Firebase auth failure into something a patient can act on.
 *
 * `Exception.message` here is the platform SDK's raw error description --
 * on iOS that is the whole NSError, `Error Domain=FIRAuthErrorDomain
 * Code=17007 ... UserInfo={...}`. Showing that to a user is both useless
 * and a review risk, so match on the stable code names Firebase embeds in
 * the text and fall back to a plain sentence.
 */
internal fun friendlyAuthError(e: Throwable): String {
    // Keep the original where it can be read. AnteroomErrors is not the
    // place for it -- that drives a global message and would double up with
    // the banner this screen already shows, and it stores the friendly text
    // rather than the raw. The console is enough to diagnose from.
    println("[auth] ${e::class.simpleName}: ${e.message}")
    val raw = e.message ?: return "Something went wrong. Please try again."
    return when {
        raw.contains("EMAIL_ALREADY_IN_USE", true) || raw.contains("already in use", true) ->
            "That email already has an account. Switch to Sign in below."
        raw.contains("USER_NOT_FOUND", true) || raw.contains("no user record", true) ->
            "No account found for that email. Create one below."
        raw.contains("WRONG_PASSWORD", true) || raw.contains("INVALID_CREDENTIAL", true) ||
            raw.contains("INVALID_LOGIN_CREDENTIALS", true) || raw.contains("password is invalid", true) ->
            "Email or password is incorrect."
        raw.contains("INVALID_EMAIL", true) -> "That email address doesn't look right."
        raw.contains("WEAK_PASSWORD", true) -> "Password must be at least 6 characters."
        raw.contains("NETWORK", true) -> "No connection. Check your internet and try again."
        raw.contains("TOO_MANY_REQUESTS", true) -> "Too many attempts. Wait a moment, then try again."
        raw.contains("USER_DISABLED", true) -> "This account has been disabled."
        // Not an auth code. Sign-up also writes the user's Firestore document,
        // so a PERMISSION_DENIED or an offline failure lands here -- and those
        // are exactly what userMessage() already knows how to phrase. Falling
        // back to a generic sentence threw that information away.
        else -> e.userMessage()
    }
}

@Composable
fun AuthScreen(
    initialMode: AuthMode = AuthMode.SignUp,
    onBack: () -> Unit,
    authService: AuthService
) {
    var mode by remember { mutableStateOf(initialMode) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val validEmail = remember(email) {
        val trimmed = email.trim()
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        emailRegex.matches(trimmed)
    }
    val validPassword = remember(password) { password.length >= 6 }
    val canSubmit = validEmail && validPassword && !loading

    val scrollState = rememberScrollState()

    fun submit() {
        if (!canSubmit) return
        error = null
        notice = null
        loading = true
        scope.launch {
            try {
                if (mode == AuthMode.SignUp) {
                    authService.signUpEmail(email.trim().lowercase(), password)
                } else {
                    authService.signInEmail(email.trim().lowercase(), password)
                }
            } catch (e: Exception) {
                error = friendlyAuthError(e)
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnteroomColors.Surface)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Back Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .padding(8.dp)
                ) {
                    Text(
                        text = "‹",
                        color = AnteroomColors.OnSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title & Subtitle
                Text(
                    text = if (mode == AuthMode.SignUp) "Create your account" else "Welcome back",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.OnSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (mode == AuthMode.SignUp)
                        "Your medical documents, organized and clinic-ready."
                    else
                        "Pick up where you left off.",
                    fontSize = 15.sp,
                    color = AnteroomColors.Muted,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Email field
                Text(
                    text = "Email",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null; notice = null },
                    placeholder = { Text("you@example.com", color = AnteroomColors.Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnteroomColors.BrandPrimary,
                        unfocusedBorderColor = AnteroomColors.BorderStrong,
                        focusedContainerColor = AnteroomColors.Surface,
                        unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Password field
                Text(
                    text = "Password",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null; notice = null },
                    placeholder = { Text("At least 6 characters", color = AnteroomColors.Muted) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        Text(
                            text = if (showPassword) "Hide" else "Show",
                            color = AnteroomColors.BrandPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { showPassword = !showPassword }
                                .padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnteroomColors.BrandPrimary,
                        unfocusedBorderColor = AnteroomColors.BorderStrong,
                        focusedContainerColor = AnteroomColors.Surface,
                        unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                    )
                )

                // Forgot password -- the only recovery path, so it lives next
                // to the field it recovers.
                if (mode == AuthMode.SignIn) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = if (resetting) "Sending..." else "Forgot password?",
                            color = AnteroomColors.BrandPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(enabled = !resetting && !loading) {
                                    error = null
                                    notice = null
                                    if (!validEmail) {
                                        error = "Enter your email address first, then tap Forgot password."
                                    } else {
                                        resetting = true
                                        val target = email.trim().lowercase()
                                        scope.launch {
                                            try {
                                                authService.sendPasswordReset(target)
                                                // Deliberately not confirming whether the
                                                // address is registered -- that would let
                                                // anyone enumerate accounts.
                                                notice = "If an account exists for $target, a reset link is on its way. Check spam too."
                                            } catch (e: Exception) {
                                                error = friendlyAuthError(e)
                                            } finally {
                                                resetting = false
                                            }
                                        }
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }

                // Notice (password reset confirmation)
                if (notice != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F1EC), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = notice ?: "",
                            color = AnteroomColors.BrandPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Error Message
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = error ?: "",
                            color = AnteroomColors.Error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Submit Button
                Button(
                    onClick = { submit() },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnteroomColors.BrandPrimary,
                        disabledContainerColor = AnteroomColors.BrandPrimary.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (mode == AuthMode.SignUp) "Continue" else "Sign in",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

            }

            // Mode Toggle Link at Bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val toggleText = buildAnnotatedString {
                    append(if (mode == AuthMode.SignUp) "Already have an account? " else "New to Anteroom? ")
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = AnteroomColors.BrandPrimary
                        )
                    ) {
                        append(if (mode == AuthMode.SignUp) "Sign in" else "Create account")
                    }
                }

                Text(
                    text = toggleText,
                    fontSize = 14.sp,
                    color = AnteroomColors.OnSurfaceSecondary,
                    modifier = Modifier
                        .clickable {
                            error = null
                            notice = null
                            mode = if (mode == AuthMode.SignUp) AuthMode.SignIn else AuthMode.SignUp
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}
