package com.wanderlog.android.presentation.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderlog.android.R

@Composable
fun OpenAiApiKeyHelpDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    introductoryText: String? = null
) {
    val appName = stringResource(R.string.app_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                buildString {
                    introductoryText?.trim()?.takeIf { it.isNotBlank() }?.let {
                        append(it)
                        append("\n\n")
                    }
                    append("1. Sign in to your OpenAI account and create an API key from the API keys page.\n\n")
                    append("2. Copy that key and paste it into the OpenAI API Key field in $appName Settings.\n\n")
                    append(
                        "Tip: if you enable data sharing in OpenAI under Settings > Data controls > Sharing, OpenAI can grant free daily tokens. That is typically enough to cover this app's normal API usage, as long as you do not upload large images or rasterize PDFs heavily."
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

@Composable
fun AiOnboardingDialog(
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val appName = stringResource(R.string.app_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up AI features") },
        text = {
            Text(
                buildString {
                    append("AI features such as itinerary generation, Ask About Trip, and booking import need an OpenAI or Gemini API key before they can be used.\n\n")
                    append("Open $appName Settings to choose your preferred AI provider and paste your API key. Tap the help icon next to the key field in Settings if you need a link to create one.")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun GeminiApiKeyHelpDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val appName = stringResource(R.string.app_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                buildString {
                    append("1. Sign in to Google AI Studio and create an API key from the API keys page.\n\n")
                    append("2. Copy that key and paste it into the Gemini API Key field in $appName Settings.\n\n")
                    append(
                        "Tip: Google AI Studio offers a free tier for Gemini Flash and Flash-Lite models with generous daily limits for personal use."
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
