package com.example.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.session.SecureSessionManager
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.repository.AuthenticationRepository
import com.example.domain.repository.MunowatchRepository
import com.example.domain.repository.PlaybackRepository
import com.example.ui.components.TvButton
import com.example.ui.components.TvTopBar
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchError
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val maskedSession: String = "",
    val sessionStatus: String = "NOT_AUTHENTICATED",
    val subscriptionDays: Int? = null,
    val diagnostics: DiagnosticsInfo? = null,
    val isClearing: Boolean = false
)

class SettingsViewModel(
    private val authRepository: AuthenticationRepository,
    private val munowatchRepository: MunowatchRepository,
    private val playbackRepository: PlaybackRepository,
    private val sessionManager: SecureSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val diag = munowatchRepository.getDiagnostics()
            val subDays = munowatchRepository.getRemainingSubscriptionDays().getOrNull()
            val hasSession = authRepository.hasStoredSession()
            val status = if (hasSession) "ACCOUNT SIGNED IN" else "DIRECT SERVER STREAMING"
            val masked = if (hasSession) sessionManager.getMaskedSessionPreview() else "Direct Media Server Mode (Active)"
            _uiState.value = _uiState.value.copy(
                maskedSession = masked,
                sessionStatus = status,
                subscriptionDays = subDays,
                diagnostics = diag
            )
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.clearSession()
            com.example.data.repository.MediaCache.clear()
            onLogoutComplete()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true)
            com.example.data.repository.MediaCache.clear()
            loadSettings()
            _uiState.value = _uiState.value.copy(isClearing = false)
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (String) -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground)
    ) {
        TvTopBar(
            currentRoute = "settings",
            onNavigate = onNavigate
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "SETTINGS & TV DIAGNOSTICS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // 1. Direct Streaming & Server Engine Panel
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MunowatchSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MunowatchGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Streaming Engine & Server Connection",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Streaming Mode",
                                    color = MunowatchTextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "DIRECT SERVER STREAMING (No Cookie Required)",
                                    color = MunowatchGreenLight,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connected directly to Twolekede & Movviez media servers.",
                                    color = MunowatchTextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Surface(
                                color = MunowatchGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MunowatchGreen),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "DIRECT SERVER MODE",
                                    color = MunowatchGreenLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (state.sessionStatus == "ACCOUNT SIGNED IN") {
                                TvButton(
                                    text = "Sign Out",
                                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                                    onClick = { viewModel.logout { onNavigateToAuth() } },
                                    isPrimary = false
                                )
                            } else {
                                TvButton(
                                    text = "Sign In to Account",
                                    icon = Icons.Default.AccountCircle,
                                    onClick = onNavigateToAuth,
                                    isPrimary = true
                                )
                            }
                            TvButton(
                                text = "Browse TV Catalog",
                                icon = Icons.Default.Tv,
                                onClick = { onNavigate("home") },
                                isPrimary = state.sessionStatus == "ACCOUNT SIGNED IN"
                            )
                            TvButton(
                                text = "Clear Cache & Reset",
                                icon = Icons.Default.Refresh,
                                onClick = { viewModel.clearHistory() },
                                isPrimary = false
                            )
                        }
                    }
                }
            }

            // 2. Diagnostics Panel
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MunowatchSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MunowatchGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "System & Connectivity Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        state.diagnostics?.let { diag ->
                            DiagnosticRow(
                                title = "Internet Access",
                                value = if (diag.networkReachable) "CONNECTED" else "DISCONNECTED",
                                isSuccess = diag.networkReachable
                            )
                            DiagnosticRow(
                                title = "Munowatch Backend (munowatch.com)",
                                value = if (diag.munowatchReachable) "REACHABLE (HTTP ${diag.lastHttpStatus})" else "UNREACHABLE",
                                isSuccess = diag.munowatchReachable
                            )
                            DiagnosticRow(
                                title = "Media3 ExoPlayer Engine",
                                value = diag.media3Version,
                                isSuccess = true
                            )
                            DiagnosticRow(
                                title = "Device Hardware & OS",
                                value = "${diag.deviceModel} • ${diag.androidVersion}",
                                isSuccess = true
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TvButton(
                            text = "Refresh Diagnostics",
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.loadSettings() },
                            isPrimary = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    title: String,
    value: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MunowatchTextSecondary,
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isSuccess) MunowatchGreen else MunowatchError,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                color = if (isSuccess) MunowatchGreenLight else MunowatchError,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
