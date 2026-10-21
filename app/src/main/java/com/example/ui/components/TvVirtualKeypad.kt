package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MunowatchFocusBorder
import com.example.ui.theme.MunowatchGreen
import com.example.ui.theme.MunowatchGreenLight
import com.example.ui.theme.MunowatchSurfaceElevated
import com.example.ui.theme.MunowatchTextPrimary

/**
 * TV D-pad Remote On-Screen Keypad for entering or pasting PHPSESSID.
 */
@Composable
fun TvVirtualKeypad(
    onCharAdded: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onUseTestToken: (() -> Unit)? = null,
    doneText: String = "VALIDATE & ENTER"
) {
    val context = LocalContext.current
    var isCaps by remember { mutableStateOf(false) }
    val row1 = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    val row2 = if (isCaps) listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J') else listOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j')
    val row3 = if (isCaps) listOf('K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T') else listOf('k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't')
    val row4 = if (isCaps) listOf('U', 'V', 'W', 'X', 'Y', 'Z', '@', '.', '-', '_') else listOf('u', 'v', 'w', 'x', 'y', 'z', '@', '.', '-', '_')
    val row5 = listOf('!', '?', '#', '$', '%', '&', '*', '+', '=', '/')

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Alphanumeric rows
        KeypadRow(items = row1, onCharClick = onCharAdded)
        KeypadRow(items = row2, onCharClick = onCharAdded)
        KeypadRow(items = row3, onCharClick = onCharAdded)
        KeypadRow(items = row4, onCharClick = onCharAdded)
        KeypadRow(items = row5, onCharClick = onCharAdded)

        Spacer(modifier = Modifier.height(6.dp))

        // Action Keys Rows
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onUseTestToken != null) {
                    TvKeypadActionKey(
                        text = "USE TEST TOKEN",
                        onClick = onUseTestToken,
                        isPrimary = true,
                        width = 150
                    )
                }
                TvKeypadActionKey(
                    text = "PASTE CLIPBOARD",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            // Extract PHPSESSID if string contains "PHPSESSID="
                            val cleaned = if (clip.contains("PHPSESSID=")) {
                                clip.substringAfter("PHPSESSID=").substringBefore(";").trim()
                            } else {
                                clip.trim()
                            }
                            onPaste(cleaned)
                        }
                    },
                    width = 150
                )
                TvKeypadActionKey(
                    text = doneText,
                    onClick = onDone,
                    isPrimary = true,
                    width = 160
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvKeypadActionKey(
                    text = if (isCaps) "CAPS: ABC" else "CAPS: abc",
                    onClick = { isCaps = !isCaps },
                    isPrimary = isCaps,
                    width = 110
                )
                TvKeypadActionKey(
                    text = "BACKSPACE",
                    onClick = onBackspace,
                    width = 130
                )
                TvKeypadActionKey(
                    text = "CLEAR ALL",
                    onClick = onClear,
                    width = 110
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(
    items: List<Char>,
    onCharClick: (Char) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { char ->
            TvKeyButton(char = char, onClick = { onCharClick(char) })
        }
    }
}

@Composable
private fun TvKeyButton(
    char: Char,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(100),
        label = "key_scale"
    )

    Surface(
        modifier = Modifier
            .size(width = 30.dp, height = 36.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("key_$char"),
        shape = RoundedCornerShape(6.dp),
        color = if (isFocused) MunowatchGreenLight else MunowatchSurfaceElevated,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color.Transparent
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = char.toString(),
                color = if (isFocused) Color.Black else MunowatchTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun TvKeypadActionKey(
    text: String,
    onClick: () -> Unit,
    width: Int,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(100),
        label = "action_key_scale"
    )

    Surface(
        modifier = Modifier
            .size(width = width.dp, height = 36.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .testTag("action_key_${text.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(6.dp),
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
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isPrimary && !isFocused) Color.White else Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
