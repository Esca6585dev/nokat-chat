package com.nokat.chat.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nokat.chat.R
import com.nokat.chat.util.asString

/** Name / username / bio form bound to [ProfileViewModel]. */
@Composable
fun ProfileFields(vm: ProfileViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val error = vm.error
    Column(modifier) {
        OutlinedTextField(
            value = vm.name,
            onValueChange = vm::onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.name_label)) },
            singleLine = true,
            shape = shape,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.username,
            onValueChange = vm::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.username_label)) },
            prefix = { Text("@") },
            supportingText = { Text(stringResource(R.string.username_hint)) },
            singleLine = true,
            shape = shape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = vm.bio,
            onValueChange = vm::onBioChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.bio_label)) },
            minLines = 2,
            maxLines = 4,
            shape = shape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
