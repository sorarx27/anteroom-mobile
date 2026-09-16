package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch

enum class AuthMode {
    SignIn, SignUp
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
        loading = true
        scope.launch {
            try {
                if (mode == AuthMode.SignUp) {
                    authService.signUpEmail(email.trim().lowercase(), password)
                } else {
                    authService.signInEmail(email.trim().lowercase(), password)
                }
            } catch (e: Exception) {
                error = e.message ?: "Authentication failed. Please check your credentials."
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
                    onValueChange = { email = it },
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
                    onValueChange = { password = it },
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

                Spacer(modifier = Modifier.height(24.dp))

                // Divider Row ("or")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = AnteroomColors.Border
                    )
                    Text(
                        text = "or",
                        color = AnteroomColors.Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = AnteroomColors.Border
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Google Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(
                            width = 1.dp,
                            color = AnteroomColors.BorderStrong,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(AnteroomColors.Surface)
                        .clickable(enabled = !loading) {
                            error = null
                            loading = true
                            scope.launch {
                                try {
                                    authService.signInWithGoogle()
                                } catch (e: Exception) {
                                    error = e.message ?: "Google sign-in is being initialized."
                                } finally {
                                    loading = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "G",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AnteroomColors.BrandPrimary
                        )
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AnteroomColors.OnSurface
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
                            mode = if (mode == AuthMode.SignUp) AuthMode.SignIn else AuthMode.SignUp
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}
