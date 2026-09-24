package com.familyfinance.app.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyfinance.app.data.remote.FirebaseConfig
import com.familyfinance.app.R
import com.familyfinance.app.ui.common.BaseViewModel
import com.familyfinance.app.ui.common.ObserveMessages
import com.familyfinance.app.ui.common.rememberMessageHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: com.familyfinance.app.domain.repository.AuthRepository,
    @ApplicationContext context: Context,
) : BaseViewModel() {

    /** True when no Firebase project is configured; the UI must say so plainly. */
    val isDemoMode: Boolean = !FirebaseConfig.isConfigured(context)

    var mode by mutableStateOf(AuthMode.SIGN_IN)
        private set
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var displayName by mutableStateOf("")
        private set

    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.length >= 8 &&
            (mode == AuthMode.SIGN_IN || (displayName.isNotBlank() && password == confirmPassword))

    fun onEmail(value: String) { email = value.trim() }
    fun onPassword(value: String) { password = value }
    fun onConfirmPassword(value: String) { confirmPassword = value }
    fun onDisplayName(value: String) { displayName = value }

    fun toggleMode() {
        mode = if (mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN
        password = ""
        confirmPassword = ""
    }

    fun submit() {
        if (!canSubmit) {
            error(
                when {
                    email.isBlank() -> "Enter your email address."
                    password.length < 8 -> "Password must be at least 8 characters."
                    mode == AuthMode.SIGN_UP && displayName.isBlank() -> "Enter your name."
                    password != confirmPassword -> "The two passwords do not match."
                    else -> "Please check the form."
                }
            )
            return
        }
        run {
            when (mode) {
                AuthMode.SIGN_IN -> auth.signIn(email, password)
                AuthMode.SIGN_UP -> auth.signUp(email, password, displayName)
            }
        }
    }

    fun sendPasswordReset() {
        if (email.isBlank()) {
            error("Enter your email address first.")
            return
        }
        run(onSuccess = { info("Password reset email sent to $email.") }) {
            auth.sendPasswordReset(email)
        }
    }

    fun googleSignIn() {
        error("Google Sign-In needs a Firebase project. Add google-services.json to enable it.")
    }
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val host = rememberMessageHost()
    var passwordVisible by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsState()

    ObserveMessages(viewModel, host)

    Scaffold(
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Family Finance logo",
                    modifier = Modifier.size(88.dp),
                )
            }
            Text(
                "Family Finance",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Private accounts for every member. One shared family dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            if (viewModel.isDemoMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Demo mode: no Firebase project is configured, so the app runs fully offline " +
                                "with sample data. Add google-services.json to use real accounts and sync.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(
                if (viewModel.mode == AuthMode.SIGN_IN) "Sign in" else "Create your account",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.mode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = viewModel.displayName,
                    onValueChange = viewModel::onDisplayName,
                    label = { Text("Full name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmail,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (viewModel.mode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done,
                ),
                supportingText = { Text("At least 8 characters") },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.mode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = viewModel.confirmPassword,
                    onValueChange = viewModel::onConfirmPassword,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = viewModel.confirmPassword.isNotEmpty() &&
                        viewModel.confirmPassword != viewModel.password,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(if (viewModel.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
            }

            OutlinedButton(
                onClick = viewModel::googleSignIn,
                enabled = !viewModel.isDemoMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Continue with Google")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::toggleMode) {
                    Text(if (viewModel.mode == AuthMode.SIGN_IN) "Create an account" else "I already have an account")
                }
                if (viewModel.mode == AuthMode.SIGN_IN) {
                    TextButton(onClick = viewModel::sendPasswordReset) { Text("Forgot password?") }
                }
            }

            Text(
                "Each member has their own login. There is no shared family password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
