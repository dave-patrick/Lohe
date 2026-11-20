package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.LoheUserSettings
import com.example.edition.Edition
import com.example.edition.LoheBillingState
import com.example.ui.components.AboutKaniHeroSection
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark
import com.example.ui.theme.LoheSurfaceDark
import com.example.ui.theme.LoheTextMutedDark
import com.example.ui.theme.LoheTextPrimaryDark

@Composable
fun AboutSettingsSubScreen(
    settings: LoheUserSettings,
    billingState: LoheBillingState,
    onOpenProModal: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LoheBackgroundDark)
            .testTag("about_sub_screen"),
        contentPadding = PaddingValues(bottom = 48.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AboutKaniHeroSection(reducedMotion = settings.reducedMotion)
        }

        item {
            Text(
                text = "Privacy policy",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LoheBrightBlueText,
                modifier = Modifier
                    .clickable { showPrivacyPolicy = true }
                    .padding(vertical = 4.dp, horizontal = 12.dp)
                    .testTag("about_privacy_policy_link")
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "To hear. To listen. To take something to heart.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoheBrightBlueText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "In Hawaiian, that is Lohe (lōhe). We built the app around the simple idea of listening on your own terms. Whether you are into gripping stories, honest conversations, the daily news, or a deep dive on a random obsession, Lohe lets you hear what you want, exactly how you want to hear it. It stays out of your way and remembers where you left off.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = LoheTextPrimaryDark
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Ua lohe ‘oe?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoheTextPrimaryDark
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Kani",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoheBrightBlueText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "That little dragonfly on your home screen is Kani. His name comes from the Hawaiian idea of listening with quiet, focused attention. He keeps an eye on your library so your next listen is always ready when you are.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = LoheTextPrimaryDark
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Credits",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoheBrightBlueText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "An Atomic 22 Industries app.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = LoheTextPrimaryDark
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.atomic22_logo),
                    contentDescription = "Atomic 22 Industries",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(38.dp)
                        .width(196.dp)
                        .testTag("about_atomic22_logo")
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "© 2026 Atomic 22 Industries\nKani mascot by Atomic 22 Design",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = LoheTextMutedDark,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (Edition.HAS_PRO) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LoheSurfaceCardDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .border(1.dp, LoheBorderDark, RoundedCornerShape(14.dp))
                        .testTag("about_pro_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (settings.isProUnlocked) "Lohe Pro Bundle Active"
                            else "Lohe Pro Bundle (${billingState.formattedPrice})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LoheTextPrimaryDark
                        )
                        Text(
                            text = if (settings.isProUnlocked) {
                                "Unlocked via Google Play Billing • Floating mini-player, extra queue styles & Google Drive AppData sync active."
                            } else {
                                "Optional Google Play subscription (${billingState.statusMessage})."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LoheTextMutedDark
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!settings.isProUnlocked) {
                                Button(
                                    onClick = onOpenProModal,
                                    colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Upgrade", color = Color.White)
                                }
                            }
                            OutlinedButton(
                                onClick = onRestorePurchases,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Restore Purchases", color = LoheCyanAccent)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            containerColor = LoheSurfaceDark,
            titleContentColor = LoheTextPrimaryDark,
            textContentColor = LoheTextMutedDark,
            title = { Text("Lohe Privacy Policy") },
            text = {
                Text(
                    "Lohe collects zero telemetry, zero analytics, zero crash tracking, and zero third-party ad SDKs.\n\nAll subscriptions, playlists, queue positions, and listening stats stay 100% on your device unless you explicitly export or sync your own backup.\n\nContact: atomic22industries@gmail.com"
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyPolicy = false },
                    colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue)
                ) { Text("Close", color = Color.White) }
            }
        )
    }
}
