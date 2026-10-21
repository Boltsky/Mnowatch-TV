package com.example.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.AuthenticationRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvVirtualKeypad
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchError
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode {
    ACCOUNT_LOGIN,
    DIRECT_SERVER,
    SESSION_TOKEN
}

data class AuthUiState(
    val emailInput: String = "",
    val passwordInput: String = "",
    val sessionInput: String = "f32a41dacb33fb4175640b14530f03b0",
    val activeField: String = "email", // "email", "password", or "session"
    val authMode: AuthMode = AuthMode.ACCOUNT_LOGIN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthenticationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun selectField(field: String) {
        _uiState.value = _uiState.value.copy(activeField = field, errorMessage = null)
    }

    fun setAuthMode(mode: AuthMode) {
        val defaultField = when (mode) {
            AuthMode.ACCOUNT_LOGIN -> "email"
            AuthMode.SESSION_TOKEN -> "session"
            AuthMode.DIRECT_SERVER -> "email"
        }
        _uiState.value = _uiState.value.copy(authMode = mode, activeField = defaultField, errorMessage = null)
    }

    fun appendChar(c: Char) {
        val s = _uiState.value
        when (s.activeField) {
            "email" -> {
                if (s.emailInput.length < 80) {
                    _uiState.value = s.copy(emailInput = s.emailInput + c, errorMessage = null)
                }
            }
            "password" -> {
                if (s.passwordInput.length < 80) {
                    _uiState.value = s.copy(passwordInput = s.passwordInput + c, errorMessage = null)
                }
            }
            "session" -> {
                if (s.sessionInput.length < 64) {
                    _uiState.value = s.copy(sessionInput = s.sessionInput + c, errorMessage = null)
                }
            }
        }
    }

    fun backspace() {
        val s = _uiState.value
        when (s.activeField) {
            "email" -> {
                if (s.emailInput.isNotEmpty()) {
                    _uiState.value = s.copy(emailInput = s.emailInput.dropLast(1), errorMessage = null)
                }
            }
            "password" -> {
                if (s.passwordInput.isNotEmpty()) {
                    _uiState.value = s.copy(passwordInput = s.passwordInput.dropLast(1), errorMessage = null)
                }
            }
            "session" -> {
                if (s.sessionInput.isNotEmpty()) {
                    _uiState.value = s.copy(sessionInput = s.sessionInput.dropLast(1), errorMessage = null)
                }
            }
        }
    }

    fun clear() {
        val s = _uiState.value
        when (s.activeField) {
            "email" -> _uiState.value = s.copy(emailInput = "", errorMessage = null)
            "password" -> _uiState.value = s.copy(passwordInput = "", errorMessage = null)
            "session" -> _uiState.value = s.copy(sessionInput = "", errorMessage = null)
        }
    }

    fun setInput(input: String) {
        val s = _uiState.value
        when (s.activeField) {
            "email" -> _uiState.value = s.copy(emailInput = input.trim(), errorMessage = null)
            "password" -> _uiState.value = s.copy(passwordInput = input, errorMessage = null)
            "session" -> _uiState.value = s.copy(sessionInput = input.trim(), errorMessage = null)
        }
    }

    fun loginWithAccount(onSuccess: () -> Unit) {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter both email and password")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Login failed. Please verify your credentials."
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err)
            }
        }
    }

    fun launchDirectServerMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.clearSession()
            _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            onSuccess()
        }
    }

    fun applyTestTokenAndLaunch(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (token.isNotBlank()) {
                authRepository.saveSession(token)
            }
            _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            onSuccess()
        }
    }

    fun validateAndSave(onSuccess: () -> Unit) {
        val input = _uiState.value.sessionInput.trim()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            if (input.isNotBlank()) {
                authRepository.saveSession(input)
            }
            _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            onSuccess()
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit,
    onSkipToGuest: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mode Selector Tabs at Top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthModeTab(
                    title = "Munowatch Account",
                    icon = Icons.Default.AccountCircle,
                    isSelected = state.authMode == AuthMode.ACCOUNT_LOGIN,
                    onClick = { viewModel.setAuthMode(AuthMode.ACCOUNT_LOGIN) }
                )
                AuthModeTab(
                    title = "Direct Server Mode (Free Guest)",
                    icon = Icons.Default.Dns,
                    isSelected = state.authMode == AuthMode.DIRECT_SERVER,
                    onClick = { viewModel.setAuthMode(AuthMode.DIRECT_SERVER) }
                )
                AuthModeTab(
                    title = "Session Token (Advanced)",
                    icon = Icons.Default.Key,
                    isSelected = state.authMode == AuthMode.SESSION_TOKEN,
                    onClick = { viewModel.setAuthMode(AuthMode.SESSION_TOKEN) }
                )
            }

            // Main Content Area (Left: Inputs & Actions, Right: Keypad)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Instructions and Input Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state.authMode) {
                        AuthMode.ACCOUNT_LOGIN -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MunowatchGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "MUNOWATCH ACCOUNT SIGN IN",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Sign in to stream subscriber movies and series in 1080p Full HD via high-speed Bunny CDN with zero IP bandwidth limits.",
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Email Field
                            Text(
                                text = "EMAIL / USERNAME",
                                color = if (state.activeField == "email") MunowatchGreen else MunowatchTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TvInputFieldBox(
                                value = state.emailInput,
                                placeholder = "Enter your Munowatch email...",
                                isActive = state.activeField == "email",
                                isPassword = false,
                                onClick = { viewModel.selectField("email") },
                                testTag = "email_input_field"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Password Field
                            Text(
                                text = "PASSWORD",
                                color = if (state.activeField == "password") MunowatchGreen else MunowatchTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TvInputFieldBox(
                                value = state.passwordInput,
                                placeholder = "Enter your password...",
                                isActive = state.activeField == "password",
                                isPassword = true,
                                onClick = { viewModel.selectField("password") },
                                testTag = "password_input_field"
                            )

                            if (state.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.errorMessage ?: "",
                                    color = MunowatchError,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (state.isLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        color = MunowatchGreen,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Signing into Munowatch...",
                                        color = MunowatchTextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TvButton(
                                            text = "Sign In to Account",
                                            onClick = { viewModel.loginWithAccount(onAuthSuccess) },
                                            isPrimary = true
                                        )
                                        TvButton(
                                            text = if (state.activeField == "email") "Switch to Password" else "Switch to Email",
                                            onClick = {
                                                if (state.activeField == "email") viewModel.selectField("password")
                                                else viewModel.selectField("email")
                                            },
                                            isPrimary = false
                                        )
                                    }
                                    TvButton(
                                        text = "Continue in Free Guest Mode",
                                        onClick = { viewModel.launchDirectServerMode(onAuthSuccess) },
                                        isPrimary = false
                                    )
                                }
                            }
                        }

                        AuthMode.DIRECT_SERVER -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MunowatchGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "DIRECT SERVER STREAMING",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Direct Server Streaming bypasses PHP session cookies entirely. The TV catalog, movie details, and series episodes load directly from Twolekede and Movviez media servers.\n\nNote: If daily IP limits are reached on free servers, signing in with your Munowatch account unlocks unlimited Bunny CDN streaming.",
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                TvButton(
                                    text = "Enter Direct Server Mode",
                                    onClick = { viewModel.launchDirectServerMode(onAuthSuccess) },
                                    isPrimary = true
                                )
                                TvButton(
                                    text = "Sign In with Account",
                                    onClick = { viewModel.setAuthMode(AuthMode.ACCOUNT_LOGIN) },
                                    isPrimary = false
                                )
                                TvButton(
                                    text = "Browse Free Catalog",
                                    onClick = onSkipToGuest,
                                    isPrimary = false
                                )
                            }
                        }

                        AuthMode.SESSION_TOKEN -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MunowatchGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "SESSION TOKEN (PHPSESSID)",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Enter a 32-character Munowatch session token directly, or paste from clipboard.",
                                color = MunowatchTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "SESSION TOKEN",
                                color = MunowatchGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TvInputFieldBox(
                                value = state.sessionInput,
                                placeholder = "Enter 32-character token...",
                                isActive = state.activeField == "session",
                                isPassword = false,
                                isMaskedSession = true,
                                onClick = { viewModel.selectField("session") },
                                testTag = "phpsessid_input_field"
                            )

                            if (state.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.errorMessage ?: "",
                                    color = MunowatchError,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                TvButton(
                                    text = "Save & Launch",
                                    onClick = { viewModel.validateAndSave(onAuthSuccess) },
                                    isPrimary = true
                                )
                                TvButton(
                                    text = "Direct Server Mode",
                                    onClick = { viewModel.launchDirectServerMode(onAuthSuccess) },
                                    isPrimary = false
                                )
                            }
                        }
                    }
                }

                // Right Column: TV Remote-Friendly Keypad (Only in input modes)
                if (state.authMode != AuthMode.DIRECT_SERVER) {
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val activeLabel = when (state.activeField) {
                            "email" -> "TYPING: EMAIL"
                            "password" -> "TYPING: PASSWORD"
                            else -> "TYPING: SESSION TOKEN"
                        }
                        Text(
                            text = activeLabel,
                            color = MunowatchGreenLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val doneLabel = when {
                            state.authMode == AuthMode.ACCOUNT_LOGIN && state.activeField == "email" -> "NEXT: PASSWORD"
                            state.authMode == AuthMode.ACCOUNT_LOGIN -> "SIGN IN"
                            else -> "VALIDATE & SAVE"
                        }

                        TvVirtualKeypad(
                            onCharAdded = { viewModel.appendChar(it) },
                            onBackspace = { viewModel.backspace() },
                            onClear = { viewModel.clear() },
                            onPaste = { viewModel.setInput(it) },
                            onDone = {
                                if (state.authMode == AuthMode.ACCOUNT_LOGIN) {
                                    if (state.activeField == "email") viewModel.selectField("password")
                                    else viewModel.loginWithAccount(onAuthSuccess)
                                } else {
                                    viewModel.validateAndSave(onAuthSuccess)
                                }
                            },
                            doneText = doneLabel,
                            onUseTestToken = if (state.authMode == AuthMode.SESSION_TOKEN) {
                                {
                                    viewModel.applyTestTokenAndLaunch(
                                        "f32a41dacb33fb4175640b14530f03b0",
                                        onAuthSuccess
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthModeTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MunowatchGreen.copy(alpha = 0.2f) else if (isFocused) MunowatchSurfaceElevated else MunowatchBackground,
        border = BorderStroke(
            1.dp,
            if (isFocused) MunowatchFocusBorder else if (isSelected) MunowatchGreen else MunowatchTextMuted.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MunowatchGreenLight else MunowatchTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else MunowatchTextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TvInputFieldBox(
    value: String,
    placeholder: String,
    isActive: Boolean,
    isPassword: Boolean,
    isMaskedSession: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MunowatchSurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(
                width = if (isActive || isFocused) 2.dp else 1.dp,
                color = if (isActive) MunowatchGreen else if (isFocused) MunowatchFocusBorder else MunowatchFocusBorder.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MunowatchTextMuted,
                    fontSize = 14.sp
                )
            } else {
                val displayText = when {
                    isPassword -> "•".repeat(value.length)
                    isMaskedSession && value.length > 8 -> value.take(4) + "••••••••••••••••" + value.takeLast(4)
                    else -> value
                }
                Text(
                    text = displayText,
                    color = MunowatchTextPrimary,
                    fontFamily = if (isPassword || isMaskedSession) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
