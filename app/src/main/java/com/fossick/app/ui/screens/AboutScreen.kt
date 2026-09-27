package com.fossick.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.fossick.app.ui.theme.FossickGraphite
import com.fossick.app.ui.theme.FossickHairline
import com.fossick.app.ui.theme.FossickInk

enum class AboutPage(val title: String) {
    HOME("About Fossick"),
    HOW("How Fossick Works"),
    PRIVACY("Privacy Policy"),
    TERMS("Terms & Conditions"),
    PERMISSIONS("Permissions & Device Access"),
    INDEXING("Indexing & Local Processing"),
    LICENSES("Open Source & Licenses"),
    APP_INFO("App Information")
}

private object CreatorLinks {
    const val INSTAGRAM = "https://www.instagram.com/ogzenn/"
    const val LINKEDIN = "https://www.linkedin.com/in/irfan-pathan7/"
}

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    page: AboutPage = AboutPage.HOME,
    onPageChange: (AboutPage) -> Unit = {},
    discoveredCount: Int,
    understoodCount: Int,
    pendingCount: Int,
    failedCount: Int,
    unsupportedCount: Int,
    isIndexing: Boolean,
    onAddLocation: () -> Unit,
    onManageAccess: () -> Unit,
    onUpdateIndex: () -> Unit
) {
    val context = LocalContext.current

    fun openExternal(destination: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination)))
        } catch (_: ActivityNotFoundException) {
            Unit
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != AboutPage.HOME) {
                    Text(
                        text = "←",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onPageChange(AboutPage.HOME) }
                            .padding(10.dp)
                            .semantics { contentDescription = "Back to About"; role = Role.Button }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(page.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        when (page) {
            AboutPage.HOME -> {
                item {
                    Text("Your files remember more than their names do.", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Fossick is a local-first memory search engine. Describe what you remember; Fossick searches accessible local metadata and content it has successfully understood.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
                items(AboutPage.entries.filter { it != AboutPage.HOME }, key = { it.name }) { destination ->
                    AboutNavigationRow(destination.title) { onPageChange(destination) }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("CREATED BY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Irfan Pathan", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("aka Zenn", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SocialAction("◎", "Instagram — @ogzenn") { openExternal(CreatorLinks.INSTAGRAM) }
                        SocialAction("in", "LinkedIn — Irfan Pathan") { openExternal(CreatorLinks.LINKEDIN) }
                    }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("PROJECT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Support Fossick · Contribute · Report a Problem", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Text("No public support, contribution, report or contact destination is configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            AboutPage.HOW -> item {
                AboutBody(
                    "DEVICE CONTENT → LOCAL DISCOVERY → LOCAL INDEX → PROGRESSIVE UNDERSTANDING → MEMORY SEARCH → REAL RESULT",
                    "Fossick records metadata that Android makes available, then progressively extracts text locally where a supported processor exists. Search uses the local index and never starts OCR or document parsing. Results open the original content through Android."
                )
            }

            AboutPage.PRIVACY -> item {
                AboutBody(
                    "Personal index data, extracted text, entities, and queries stay in Fossick's local app storage. The merged app manifest does not request INTERNET. Fossick includes no advertising or analytics SDK and does not send memories to a remote search or AI service.",
                    "Fossick processes supported content locally. On Android 11 and newer, its core on-device file-search function may use All Files Access for broad shared-storage discovery after you deliberately enable it in Special app access. Search queries and search results are not transmitted by Fossick.",
                    "Opening an item or creator link hands its content URI or web address to another installed app or browser. That app's own privacy practices then apply. Removing Fossick also removes its local index; the original files remain managed by Android and their source apps."
                )
            }

            AboutPage.TERMS -> item {
                AboutBody(
                    "Fossick is provided to help you index and search content that Android makes available to you. You are responsible for having the right to access and use the files and folders you select.",
                    "Availability depends on Android permissions, provider behavior, file format, and local processor support. Fossick may not extract every file or recognize every language. Search results are best-effort and do not replace your original files or backups.",
                    "Opening a result may launch a third-party viewer. Fossick does not control that app, its availability, or its handling of content. Fossick and its bundled third-party components remain subject to their respective intellectual-property and license terms.",
                    "Fossick is provided without a guarantee that every item will be discovered, understood, retained, or searchable. To the extent permitted by applicable law, the creator is not responsible for loss caused by device, storage-provider, or third-party app failures. These terms may be updated with later releases. No company or legal entity is represented here."
                )
            }

            AboutPage.PERMISSIONS -> item {
                AboutBody(
                    "Fossick uses All Files Access only for its core on-device search of supported files in broad shared storage. Android still restricts app-private locations, and Fossick never bypasses sandbox protections. MediaStore remains the source for media metadata and selected-media access remains supported.",
                    "Use Add a folder location to grant access to another document tree through Android's system picker. Fossick persists the read grant and scans only that selected tree. You can revoke access in Android settings. Content providers can still remove or deny individual items.",
                    actionLabel = "Manage storage access",
                    onAction = onManageAccess
                )
            }

            AboutPage.INDEXING -> item {
                AboutBody(
                    "$discoveredCount discovered · $understoodCount understood · $pendingCount pending · $failedCount failed · $unsupportedCount unsupported",
                    "Discovered means Fossick has metadata for an accessible item. Discovered items can be browsed, searched by metadata, and opened without waiting for OCR. Understood means a local content extractor completed successfully.",
                    "Unchanged successfully indexed items keep their extracted text. When source modification time changes, Fossick queues that item for local processing. Images use on-device Latin-script OCR. Plain text and supported modern Office files can be extracted; video and PDF remain metadata-only in this build."
                )
            }

            AboutPage.LICENSES -> item {
                AboutBody(
                    "Fossick uses AndroidX Compose, Room, Lifecycle, and related AndroidX libraries, which are distributed under Apache License 2.0. Google ML Kit Text Recognition is a Google component and is governed by Google's applicable terms.",
                    "The app does not bundle a separate in-app license browser. Dependency and license notices should be reviewed from the corresponding release artifacts before redistribution."
                )
            }

            AboutPage.APP_INFO -> item {
                val version = runCatching {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                }.getOrDefault("Unavailable")
                AboutBody("Fossick $version", "Android 8.0 and newer are supported. Fossick is designed to work locally and requires no Fossick internet connection.")
            }
        }
    }
}

@Composable
private fun AboutNavigationRow(title: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(11.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AboutBody(vararg paragraphs: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        paragraphs.forEach { paragraph ->
            Text(paragraph, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction).padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SocialAction(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}