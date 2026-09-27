package com.fossick.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fossick.app.R
import androidx.compose.material3.MaterialTheme

@Composable
fun FossickWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.fossick_wordmark),
        contentDescription = "Fossick",
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        modifier = modifier.semantics { contentDescription = "Fossick wordmark" }
    )
}