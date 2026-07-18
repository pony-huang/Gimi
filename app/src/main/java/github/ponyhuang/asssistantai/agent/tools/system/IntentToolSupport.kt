package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import android.net.Uri

internal fun nonBlank(value: String, name: String): String? = value.trim().takeIf { it.isNotEmpty() }
    ?: throw IllegalArgumentException("$name must not be blank.")

internal fun Intent.withReadGrant(uri: Uri?): Intent = apply {
    if (uri != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
