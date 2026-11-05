@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.runtime.Composable

@Composable
fun ShortcutTestScreen(onBack: () -> Unit) {
    ShortcutPortPage(onBack = onBack)
}
