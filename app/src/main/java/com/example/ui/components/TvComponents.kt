package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.model.MediaItem
import com.example.ui.theme.MunowatchBackground
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchFocusGlow
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchRatingGold
import com.example.ui.theme.MunowatchSurface
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextMuted
import com.example.ui.theme.MunowatchTextPrimary
import com.example.ui.theme.MunowatchTextSecondary

/**
 * TV Poster Card with D-pad focus scaling, glowing emerald border, and metadata badges.
 */
@Composable
fun TvMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
    cardWidth: androidx.compose.ui.unit.Dp? = 160.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "card_scale"
    )

    Column(
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth())
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = "${item.title} ${if (item.isSeries) "Series" else "Movie"}"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(MunowatchSurfaceElevated)
                .border(
                    border = if (isFocused) BorderStroke(3.dp, MunowatchFocusBorder) else BorderStroke(1.dp, Color.Transparent),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
                .testTag("media_card_${item.id}")
        ) {
            if (item.posterUrl.isNotBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(item.posterUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(item.posterUrl)
                        .size(coil.size.Size(240, 360))
                        .precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder art if poster URL is not yet loaded
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(MunowatchSurfaceElevated, MunowatchSurface)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MunowatchGreen.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Top-right series badge
            if (item.isSeries) {
                Surface(
                    color = MunowatchGreen,
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "SERIES",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Top-left VJ badge (shown on both movies and series posters)
            if (item.vj.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = item.vj.take(14),
                        color = MunowatchGreenLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Bottom gradient overlay for focus glow
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MunowatchFocusGlow)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = item.title,
            color = if (isFocused) MunowatchGreenLight else MunowatchTextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Metadata subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (item.year.isNotBlank()) {
                Text(
                    text = item.year,
                    color = MunowatchTextMuted,
                    fontSize = 11.sp
                )
            }
            if (item.quality.isNotBlank()) {
                Text(
                    text = item.quality,
                    color = MunowatchGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * TV Focusable Action Button with high contrast and D-pad states
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    isCompact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(100),
        label = "btn_scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter ||
                     keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .testTag("tv_btn_${text.lowercase().replace(" ", "_")}"),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp),
        color = when {
            isFocused -> if (isPrimary) MunowatchGreenLight else Color.White
            isPrimary -> MunowatchGreen
            else -> MunowatchSurfaceElevated
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color.Transparent
        )
    ) {
        val contentColor = if (isFocused) Color.Black else Color.White
        Row(
            modifier = Modifier
                .padding(
                    horizontal = if (isCompact) 12.dp else 20.dp,
                    vertical = if (isCompact) 4.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (isCompact) 14.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = if (isCompact) FontWeight.SemiBold else FontWeight.Bold,
                fontSize = if (isCompact) 12.sp else 15.sp
            )
        }
    }
}

/**
 * TV Top Navigation Bar with branding and remote tabs
 */
@Composable
fun TvTopBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    subscriptionDays: Int? = null,
    onDownFocusRequester: FocusRequester? = null
) {
    val navItems = listOf(
        Pair("home", "Home"),
        Pair("movies", "Movies"),
        Pair("series", "Series"),
        Pair("categories", "Categories"),
        Pair("search", "Search"),
        Pair("settings", "Settings")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo & TV badge
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MUNO",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "WATCH",
                style = MaterialTheme.typography.headlineSmall,
                color = MunowatchGreen,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MunowatchGreen.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MunowatchGreen),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "TV",
                    color = MunowatchGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }

        // Navigation Items
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { (route, title) ->
                val isSelected = currentRoute.startsWith(route)
                TvNavTab(
                    title = title,
                    isSelected = isSelected,
                    onClick = { onNavigate(route) },
                    modifier = if (onDownFocusRequester != null) Modifier.focusProperties { down = onDownFocusRequester } else Modifier
                )
            }
        }

        // Subscription Status
        if (subscriptionDays != null && subscriptionDays > 0) {
            Surface(
                color = MunowatchSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MunowatchGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MunowatchGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$subscriptionDays Days Left",
                        color = MunowatchTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TvNavTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("nav_tab_${title.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        color = when {
            isFocused -> MunowatchGreenLight
            isSelected -> MunowatchSurfaceElevated
            else -> Color.Transparent
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
            color = if (isFocused) Color.White else if (isSelected) MunowatchGreen.copy(alpha = 0.6f) else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = when {
                    isFocused -> Color.Black
                    isSelected -> MunowatchGreen
                    else -> MunowatchTextSecondary
                },
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * TV Loading Indicator with cinematic dark backdrop
 */
@Composable
fun TvLoadingIndicator(
    message: String = "Loading Munowatch...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MunowatchBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = MunowatchGreen,
                strokeWidth = 4.dp,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MunowatchTextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * TV Error View with Retry and Back buttons
 */
@Composable
fun TvErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MunowatchBackground)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(560.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CONNECTION OR PLAYBACK ISSUE",
                color = Color(0xFFEF4444),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MunowatchTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            val retryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                try {
                    retryFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TvButton(
                    text = "Retry",
                    icon = Icons.Default.Refresh,
                    onClick = onRetry,
                    isPrimary = onSignIn == null,
                    modifier = Modifier.focusRequester(retryFocusRequester)
                )
                if (onSignIn != null) {
                    TvButton(
                        text = "Sign In to Munowatch",
                        icon = Icons.Default.AccountCircle,
                        onClick = onSignIn,
                        isPrimary = true
                    )
                }
                if (onBack != null) {
                    TvButton(
                        text = "Go Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        isPrimary = false
                    )
                }
            }
        }
    }
}
