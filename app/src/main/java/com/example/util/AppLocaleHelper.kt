package com.example.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import java.util.Locale

data class SupportedAppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val flagEmoji: String
)

val SUPPORTED_20_LANGUAGES = listOf(
    SupportedAppLanguage("en", "English", "English", "🇺🇸"),
    SupportedAppLanguage("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
    SupportedAppLanguage("es", "Español", "Spanish", "🇪🇸"),
    SupportedAppLanguage("fr", "Français", "French", "🇫🇷"),
    SupportedAppLanguage("de", "Deutsch", "German", "🇩🇪"),
    SupportedAppLanguage("ja", "日本語", "Japanese", "🇯🇵"),
    SupportedAppLanguage("ko", "한국어", "Korean", "🇰🇷"),
    SupportedAppLanguage("zh-CN", "简体中文", "Simplified Chinese", "🇨🇳"),
    SupportedAppLanguage("pt", "Português", "Portuguese", "🇵🇹"),
    SupportedAppLanguage("it", "Italiano", "Italian", "🇮🇹"),
    SupportedAppLanguage("ru", "Русский", "Russian", "🇷🇺"),
    SupportedAppLanguage("ar", "العربية", "Arabic", "🇸🇦"),
    SupportedAppLanguage("hi", "हिन्दी", "Hindi", "🇮🇳"),
    SupportedAppLanguage("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
    SupportedAppLanguage("th", "ภาษาไทย", "Thai", "🇹🇭"),
    SupportedAppLanguage("tr", "Türkçe", "Turkish", "🇹🇷"),
    SupportedAppLanguage("nl", "Nederlands", "Dutch", "🇳🇱"),
    SupportedAppLanguage("pl", "Polski", "Polish", "🇵🇱"),
    SupportedAppLanguage("sv", "Svenska", "Swedish", "🇸🇪"),
    SupportedAppLanguage("uk", "Українська", "Ukrainian", "🇺🇦")
)

object AppLocaleHelper {
    fun parseLocale(tag: String): Locale {
        return when (tag) {
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.forLanguageTag(tag)
        }
    }

    fun createLocalizedContext(baseContext: Context, languageTag: String): TupleLocalizedContext {
        val locale = parseLocale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val localizedCtx = baseContext.createConfigurationContext(config)
        return TupleLocalizedContext(localizedCtx, config)
    }
}

data class TupleLocalizedContext(
    val context: Context,
    val configuration: Configuration
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePickerDialog(
    selectedLanguageTag: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.language_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(370.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.language_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SUPPORTED_20_LANGUAGES.forEach { lang ->
                        val isSelected = lang.tag.equals(selectedLanguageTag, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectLanguage(lang.tag)
                                    onDismiss()
                                }
                                .testTag("language_option_${lang.tag}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = lang.flagEmoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Column {
                                        Text(
                                            text = lang.nativeName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${lang.englishName} (${lang.tag})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("language_dialog_close_btn")
            ) {
                Text(stringResource(R.string.dialog_close))
            }
        }
    )
}
