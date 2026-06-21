package com.pulsedeck.app.beta

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface BetaGateScreenState {
    data object Loading : BetaGateScreenState
    data class Blocked(val access: BetaAccessState, val license: BetaLicense?) : BetaGateScreenState
    data class Allowed(val access: BetaAccessState, val license: BetaLicense?) : BetaGateScreenState
}

private data class BetaActivationInput(
    val inviteCode: String,
    val testerEmail: String,
)

private const val ACTIVE_LICENSE_REFRESH_INTERVAL_MS = 15L * 60L * 1000L
private const val LOCKED_LICENSE_REFRESH_INTERVAL_MS = 15L * 60L * 1000L

@Composable
fun PulseDeckBetaGate(
    onFirstUsefulUi: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(context) { BetaAccessController(context) }
    val scope = rememberCoroutineScope()
    var screenState by remember { mutableStateOf<BetaGateScreenState>(BetaGateScreenState.Loading) }

    LaunchedEffect(controller) {
        val (access, license) = controller.loadInitialState()
        screenState = if (access.allowed) {
            BetaGateScreenState.Allowed(access, license)
        } else {
            BetaGateScreenState.Blocked(access, license)
        }
        onFirstUsefulUi()
        if (access.allowed) {
            val (refreshed, refreshedLicense) = controller.refreshOnline(license)
            if (!refreshed.allowed && refreshed.status.isTerminalLock()) {
                screenState = BetaGateScreenState.Blocked(refreshed, refreshedLicense)
            }
        } else if (license != null) {
            val (refreshed, refreshedLicense) = controller.refreshOnline(license)
            if (refreshed.allowed) {
                screenState = BetaGateScreenState.Allowed(refreshed, refreshedLicense)
            } else if (refreshed.status.isTerminalLock()) {
                screenState = BetaGateScreenState.Blocked(refreshed, refreshedLicense ?: license)
            }
        }
    }

    LaunchedEffect(screenState, controller) {
        when (val state = screenState) {
            is BetaGateScreenState.Allowed -> {
                var currentLicense = state.license ?: return@LaunchedEffect
                while (true) {
                    delay(ACTIVE_LICENSE_REFRESH_INTERVAL_MS)
                    val (refreshed, refreshedLicense) = controller.refreshOnline(currentLicense)
                    if (refreshed.allowed) {
                        if (refreshedLicense != null) currentLicense = refreshedLicense
                    } else if (refreshed.status.isTerminalLock()) {
                        screenState = BetaGateScreenState.Blocked(refreshed, refreshedLicense ?: currentLicense)
                        return@LaunchedEffect
                    }
                }
            }
            is BetaGateScreenState.Blocked -> {
                var currentLicense = state.license ?: return@LaunchedEffect
                while (true) {
                    delay(LOCKED_LICENSE_REFRESH_INTERVAL_MS)
                    val (refreshed, refreshedLicense) = controller.refreshOnline(currentLicense)
                    if (refreshed.allowed) {
                        screenState = BetaGateScreenState.Allowed(refreshed, refreshedLicense)
                        return@LaunchedEffect
                    }
                    if (refreshedLicense != null) currentLicense = refreshedLicense
                }
            }
            BetaGateScreenState.Loading -> Unit
        }
    }

    when (val state = screenState) {
        BetaGateScreenState.Loading -> {
            BetaGateLoading()
        }
        is BetaGateScreenState.Allowed -> {
            content()
        }
        is BetaGateScreenState.Blocked -> {
            BetaGateBlockedScreen(
                access = state.access,
                license = state.license,
                onActivate = { code ->
                    screenState = BetaGateScreenState.Loading
                    scope.launch {
                        val (activated, license) = controller.activate(code.inviteCode, code.testerEmail)
                        screenState = if (activated.allowed) {
                            BetaGateScreenState.Allowed(activated, license)
                        } else {
                            BetaGateScreenState.Blocked(activated, license)
                        }
                    }
                },
                onFeedback = { message ->
                    scope.launch {
                        val sent = controller.submitFeedback(state.license, message)
                        Toast.makeText(
                            context,
                            if (sent) "Feedback sent." else "Feedback queued for online beta setup.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onContactDeveloper = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_SUBJECT, "PulseDeck beta access")
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { Toast.makeText(context, "No email app available.", Toast.LENGTH_SHORT).show() }
                },
            )
        }
    }
}

@Composable
private fun BetaGateLoading() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(18.dp))
            Text("Checking PulseDeck beta access")
        }
    }
}

@Composable
private fun BetaGateBlockedScreen(
    access: BetaAccessState,
    license: BetaLicense?,
    onActivate: (BetaActivationInput) -> Unit,
    onFeedback: (String) -> Unit,
    onContactDeveloper: () -> Unit,
) {
    var inviteCode by remember { mutableStateOf(TextFieldValue("")) }
    var testerEmail by remember { mutableStateOf(TextFieldValue("")) }
    var feedback by remember { mutableStateOf(TextFieldValue("")) }
    val activationRequired = access.requiresActivation || access.status == BetaLicenseStatus.Missing
    val title = if (activationRequired) "PulseDeck Beta Access" else "PulseDeck beta access ended."
    val body = if (activationRequired) {
        "Enter your PulseDeck beta invite code and approved email.\nThis beta runs for 30 days on one approved device."
    } else {
        "This beta build has expired.\nThank you for testing PulseDeck."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), lineHeight = 22.sp)
            Spacer(Modifier.height(20.dp))
            BetaStatusPanel(access, license)
            if (activationRequired) {
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Invite code") },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = testerEmail,
                    onValueChange = { testerEmail = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Approved email") },
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onActivate(
                            BetaActivationInput(
                                inviteCode = inviteCode.text,
                                testerEmail = testerEmail.text,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inviteCode.text.isNotBlank() && testerEmail.text.isNotBlank(),
                ) {
                    Text("Activate")
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Send Feedback") },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onFeedback(feedback.text) },
                enabled = feedback.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send Feedback")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onContactDeveloper, modifier = Modifier.fillMaxWidth()) {
                Text("Contact Developer")
            }
        }
    }
}

@Composable
private fun BetaStatusPanel(access: BetaAccessState, license: BetaLicense?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BetaStatusRow("Status", access.status.name)
            BetaStatusRow("Message", access.message)
            BetaStatusRow("Build", PulseDeckBetaBuild.identity.betaBuildId)
            BetaStatusRow("Tier", license?.testerTier?.name ?: BetaDeviceTierPolicy.estimateTesterTier(LocalContext.current).name)
            BetaStatusRow("Expires", access.expiresAtEpochMs?.let(::formatEpochMs) ?: "Not activated")
            BetaStatusRow(
                "Offline grace",
                access.offlineGraceRemainingMs?.let { "${it / (60L * 60L * 1000L)}h remaining" } ?: "Requires server validation",
            )
        }
    }
}

@Composable
private fun BetaStatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            fontSize = 13.sp,
        )
        Text(text = value, modifier = Modifier.weight(1f), fontSize = 13.sp)
    }
}

private fun formatEpochMs(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
