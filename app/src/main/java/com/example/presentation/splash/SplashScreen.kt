package com.example.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.repository.AuthenticationRepository
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class SplashNavEvent {
    object NavigateToHome : SplashNavEvent()
    object NavigateToAuth : SplashNavEvent()
}

class SplashViewModel(
    private val authRepository: AuthenticationRepository
) : ViewModel() {

    private val _navEvents = MutableSharedFlow<SplashNavEvent>()
    val navEvents = _navEvents.asSharedFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            // Brief cinematic presentation delay for TV splash experience
            delay(1200)
            // Launch directly into Home with Direct Server Streaming (no PHP session cookie required)
            _navEvents.emit(SplashNavEvent.NavigateToHome)
        }
    }
}

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(800)
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                SplashNavEvent.NavigateToHome -> onNavigateToHome()
                SplashNavEvent.NavigateToAuth -> onNavigateToHome()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MunowatchBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MUNO",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp
                )
                Text(
                    text = "WATCH",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MunowatchGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    color = MunowatchGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "TV",
                        color = MunowatchGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Translated Movies & TV Shows",
                color = MunowatchTextSecondary,
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = MunowatchGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
