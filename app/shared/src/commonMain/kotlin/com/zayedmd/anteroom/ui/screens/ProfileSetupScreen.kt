package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import kotlinx.coroutines.launch

data class LanguageOption(val code: String, val label: String, val flag: String)
data class CountryOption(val code: String, val label: String)

private val LANGUAGES = listOf(
    LanguageOption("en", "English", "🇬🇧"),
    LanguageOption("es", "Español", "🇪🇸")
)

private val COUNTRIES = listOf(
    CountryOption("US", "United States"),
    CountryOption("ES", "Spain"),
    CountryOption("MX", "Mexico"),
    CountryOption("GB", "United Kingdom"),
    CountryOption("DE", "Germany"),
    CountryOption("FR", "France"),
    CountryOption("IT", "Italy"),
    CountryOption("PT", "Portugal"),
    CountryOption("AR", "Argentina"),
    CountryOption("BR", "Brazil"),
    CountryOption("CO", "Colombia"),
    CountryOption("CL", "Chile"),
    CountryOption("CA", "Canada"),
    CountryOption("AU", "Australia"),
    CountryOption("IE", "Ireland"),
    CountryOption("NL", "Netherlands"),
    CountryOption("BE", "Belgium"),
    CountryOption("CH", "Switzerland"),
    CountryOption("AT", "Austria"),
    CountryOption("OTHER", "Other")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    user: AppUser?,
    authService: AuthService
) {
    var name by remember { mutableStateOf(user?.name ?: "") }
    var dobYear by remember { mutableStateOf("") }
    var dobMonth by remember { mutableStateOf("") }
    var dobDay by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf<LanguageOption?>(LANGUAGES.firstOrNull()) }
    var selectedCountry by remember { mutableStateOf<CountryOption?>(COUNTRIES.firstOrNull()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val dobValid = remember(dobYear, dobMonth, dobDay) {
        val y = dobYear.toIntOrNull()
        val m = dobMonth.toIntOrNull()
        val d = dobDay.toIntOrNull()
        if (dobYear.length != 4 || y == null || m == null || d == null) {
            false
        } else {
            y in 1900..2026 && m in 1..12 && d in 1..31
        }
    }

    val canSubmit = name.trim().isNotEmpty() && dobValid && selectedLanguage != null && selectedCountry != null && !loading

    fun submit() {
        if (!canSubmit) return
        error = null
        loading = true
        scope.launch {
            try {
                val formattedDob = "${dobYear.padStart(4, '0')}-${dobMonth.padStart(2, '0')}-${dobDay.padStart(2, '0')}"
                authService.updateProfile(
                    name = name.trim(),
                    dob = formattedDob,
                    language = selectedLanguage?.code ?: "en",
                    country = selectedCountry?.label ?: "United States"
                )
            } catch (e: Exception) {
                error = e.message ?: "Could not complete profile setup"
            } finally {
                loading = false
            }
        }
    }

    val scrollState = rememberScrollState()

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
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Header
                Text(
                    text = "Set up your profile",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.OnSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This helps Anteroom personalize your medical briefs and interpret regional health terms.",
                    fontSize = 15.sp,
                    color = AnteroomColors.Muted,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Name field
                Text(
                    text = "Full Name",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Jane Doe", color = AnteroomColors.Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AnteroomColors.BrandPrimary,
                        unfocusedBorderColor = AnteroomColors.BorderStrong,
                        focusedContainerColor = AnteroomColors.Surface,
                        unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Date of Birth fields
                Text(
                    text = "Date of Birth",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Year
                    OutlinedTextField(
                        value = dobYear,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) dobYear = it },
                        placeholder = { Text("YYYY", color = AnteroomColors.Muted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnteroomColors.BrandPrimary,
                            unfocusedBorderColor = AnteroomColors.BorderStrong,
                            focusedContainerColor = AnteroomColors.Surface,
                            unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                        )
                    )
                    // Month
                    OutlinedTextField(
                        value = dobMonth,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) dobMonth = it },
                        placeholder = { Text("MM", color = AnteroomColors.Muted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnteroomColors.BrandPrimary,
                            unfocusedBorderColor = AnteroomColors.BorderStrong,
                            focusedContainerColor = AnteroomColors.Surface,
                            unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                        )
                    )
                    // Day
                    OutlinedTextField(
                        value = dobDay,
                        onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) dobDay = it },
                        placeholder = { Text("DD", color = AnteroomColors.Muted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnteroomColors.BrandPrimary,
                            unfocusedBorderColor = AnteroomColors.BorderStrong,
                            focusedContainerColor = AnteroomColors.Surface,
                            unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Language selection
                Text(
                    text = "Preferred Language",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LANGUAGES.forEach { lang ->
                        val selected = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) AnteroomColors.BrandPrimary else AnteroomColors.BorderStrong,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    if (selected) AnteroomColors.BrandTertiary else AnteroomColors.SurfaceSecondary
                                )
                                .clickable { selectedLanguage = lang },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${lang.flag} ${lang.label}",
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) AnteroomColors.BrandPrimary else AnteroomColors.OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Country selection
                Text(
                    text = "Country",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = countryMenuExpanded,
                    onExpandedChange = { countryMenuExpanded = !countryMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCountry?.label ?: "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AnteroomColors.BrandPrimary,
                            unfocusedBorderColor = AnteroomColors.BorderStrong,
                            focusedContainerColor = AnteroomColors.Surface,
                            unfocusedContainerColor = AnteroomColors.SurfaceSecondary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        COUNTRIES.forEach { country ->
                            DropdownMenuItem(
                                text = { Text(country.label) },
                                onClick = {
                                    selectedCountry = country
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Error Banner
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
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                        contentColor = Color.White
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
                            text = "Complete profile",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Sign out",
                    fontSize = 14.sp,
                    color = AnteroomColors.Muted,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            scope.launch { authService.signOut() }
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}
