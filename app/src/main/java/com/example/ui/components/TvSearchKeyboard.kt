package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MunowatchGreen

/**
 * YouTube Android TV style on-screen virtual keyboard for search.
 * Features a compact, sleek QWERTY layout with high-contrast D-pad focus states
 * and dedicated Space, Backspace, Clear, and Search action controls.
 */
@Composable
fun TvSearchKeyboard(
    onCharAdded: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowNumbers = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    val rowQwertyTop = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    val rowQwertyMid = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    val rowQwertyBot = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '-')

    Column(
        modifier = modifier.width(308.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Numbers 1-0
        KeyboardRow(items = rowNumbers, onCharClick = onCharAdded)

        // Row 2: QWERTY Top (q - p)
        KeyboardRow(items = rowQwertyTop, onCharClick = onCharAdded)

        // Row 3: QWERTY Mid (a - l)
        KeyboardRow(items = rowQwertyMid, onCharClick = onCharAdded)

        // Row 4: QWERTY Bot (z - m, -)
        KeyboardRow(items = rowQwertyBot, onCharClick = onCharAdded)

        Spacer(modifier = Modifier.height(2.dp))

        // Row 5: Action Controls (SPACE, BACKSPACE, CLEAR, SEARCH)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Space Bar
            TvSearchActionKey(
                text = "SPACE",
                width = 88,
                testTag = "key_space",
                onClick = { onCharAdded(' ') }
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Backspace Key
            TvSearchActionKey(
                icon = { tint ->
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                },
                width = 46,
                testTag = "key_backspace",
                onClick = onBackspace
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Clear Key
            TvSearchActionKey(
                text = "CLEAR",
                width = 52,
                testTag = "key_clear",
                onClick = onClear
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Search Key
            TvSearchActionKey(
                text = "SEARCH",
                isPrimary = true,
                width = 76,
                testTag = "key_search",
                onClick = onSearch
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    items: List<Char>,
    onCharClick: (Char) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { char ->
            TvSearchKeyButton(char = char, onClick = { onCharClick(char) })
        }
    }
}

/**
 * YouTube Android TV style individual key button.
 * Unfocused: dark matte pill with subtle border and crisp white text.
 * Focused: bright high-contrast pure white background with bold black text and smooth pop scale.
 */
@Composable
private fun TvSearchKeyButton(
    char: Char,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(90),
        label = "key_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color(0xFF242424),
        animationSpec = tween(90),
        label = "key_bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF101010) else Color(0xFFE2E2E2),
        animationSpec = tween(90),
        label = "key_text"
    )

    Surface(
        modifier = Modifier
            .size(width = 27.dp, height = 33.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("key_$char"),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0xFF333333)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = char.toString(),
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Action key (Space, Backspace, Clear, Search) following YouTube Android TV styling.
 */
@Composable
private fun TvSearchActionKey(
    width: Int,
    testTag: String,
    onClick: () -> Unit,
    text: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(90),
        label = "action_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> MunowatchGreen
            else -> Color(0xFF282828)
        },
        animationSpec = tween(90),
        label = "action_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF101010)
            isPrimary -> Color.White
            else -> Color(0xFFDCDCDC)
        },
        animationSpec = tween(90),
        label = "action_content_color"
    )

    Surface(
        modifier = Modifier
            .size(width = width.dp, height = 33.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag(testTag),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isPrimary -> MunowatchGreen
                else -> Color(0xFF383838)
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text != null) {
                Text(
                    text = text,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            } else if (icon != null) {
                icon(contentColor)
            }
        }
    }
}
