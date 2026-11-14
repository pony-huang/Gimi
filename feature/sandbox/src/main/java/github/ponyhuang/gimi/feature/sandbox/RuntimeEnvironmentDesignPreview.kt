package github.ponyhuang.gimi.feature.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.GimiTheme

// 独立设计草稿：所有文字和数值均为 Preview fixture，尚未接入生产导航与业务状态。
private enum class RuntimeDesignPage { Overview, Install, Download, Variables, Directory, Failure, Stop }

@Preview(name = "环境总览 · 浅色与深色", widthDp = 808, heightDp = 844)
@Composable
private fun RuntimeOverviewDesignPreview() = RuntimeDesignPair(RuntimeDesignPage.Overview)

@Preview(name = "首次准备 · 安装与下载", widthDp = 808, heightDp = 844)
@Composable
private fun RuntimeInstallDesignPreview() = RuntimeDesignPair(RuntimeDesignPage.Install, RuntimeDesignPage.Download)

@Preview(name = "环境变量 · 浅色与深色", widthDp = 808, heightDp = 844)
@Composable
private fun RuntimeVariablesDesignPreview() = RuntimeDesignPair(RuntimeDesignPage.Variables)

@Preview(name = "本地目录 · 浅色与深色", widthDp = 808, heightDp = 844)
@Composable
private fun RuntimeDirectoryDesignPreview() = RuntimeDesignPair(RuntimeDesignPage.Directory)

@Preview(name = "恢复与关闭 · 关键状态", widthDp = 808, heightDp = 844)
@Composable
private fun RuntimeRecoveryDesignPreview() = RuntimeDesignPair(RuntimeDesignPage.Failure, RuntimeDesignPage.Stop)

@Composable
private fun RuntimeDesignPair(left: RuntimeDesignPage, right: RuntimeDesignPage = left) {
    val uriHandler = LocalUriHandler.current
    val onOpenOfficialWebsite = { uriHandler.openUri("https://www.debian.org/") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f)) { GimiTheme(darkTheme = false) { RuntimeDesignPage(left, onOpenOfficialWebsite) } }
        Box(Modifier.weight(1f)) { GimiTheme(darkTheme = true) { RuntimeDesignPage(right, onOpenOfficialWebsite) } }
    }
}

@Composable
private fun RuntimeDesignPage(page: RuntimeDesignPage, onOpenOfficialWebsite: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        PreferenceScaffold(
            title = when (page) {
                RuntimeDesignPage.Overview, RuntimeDesignPage.Stop -> "运行环境"
                RuntimeDesignPage.Variables -> "环境变量"
                RuntimeDesignPage.Directory -> "本地环境目录"
                else -> "本地环境"
            },
            onBack = {},
            style = PreferenceStyle.Compact,
        ) { modifier ->
            PreferencePageContainer(modifier.verticalScroll(rememberScrollState()), style = PreferenceStyle.Compact) {
                when (page) {
                    RuntimeDesignPage.Overview, RuntimeDesignPage.Stop -> RuntimeOverviewFixture()
                    RuntimeDesignPage.Variables -> RuntimeVariablesFixture()
                    RuntimeDesignPage.Directory -> RuntimeDirectoryFixture()
                    else -> RuntimeInstallFixture(page, onOpenOfficialWebsite)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (page == RuntimeDesignPage.Stop) RuntimeStopFixture()
    }
}

@Composable
private fun RuntimeOverviewFixture() {
    PreferenceSectionTitle("执行环境")
    PreferenceGroupCard {
        PreferenceListItem(
            icon = Icons.Default.PhoneAndroid,
            title = "本地环境",
            subtitle = "已就绪 · 占用 420 MB",
            trailingContent = { GimiSwitch(true, {}) },
            showDivider = true,
        )
        PreferenceListItem(
            title = "管理环境",
            subtitle = "Debian · 本机运行",
            onClick = {},
            trailingContent = { DesignChevron() },
            showDivider = true,
        )
        PreferenceListItem(icon = Icons.Default.Tune, title = "环境变量", onClick = {},
            trailingContent = { DesignChevron() }, showDivider = true)
        PreferenceListItem(icon = Icons.Default.FolderOpen, title = "查看目录", onClick = {},
            trailingContent = { DesignChevron() })
    }
    Spacer(Modifier.height(12.dp))
    PreferenceGroupCard {
        PreferenceListItem(
            icon = Icons.Default.CloudQueue,
            title = "E2B 沙盒",
            subtitle = "已配置",
            trailingContent = { GimiSwitch(true, {}) },
            showDivider = true,
        )
        PreferenceListItem(title = "连接配置", onClick = {}, trailingContent = { DesignChevron() })
    }
}

@Composable
private fun RuntimeInstallFixture(page: RuntimeDesignPage, onOpenOfficialWebsite: () -> Unit) {
    PreferenceSectionTitle("运行组件")
    PreferenceGroupCard {
        PreferenceListItem(
            icon = Icons.Default.PhoneAndroid,
            title = "Gimi 运行组件",
            subtitle = "已安装 · 独立应用运行",
            trailingContent = {
                Icon(Icons.Default.CheckCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
    }
    PreferenceSectionTitle("基础环境")
    PreferenceGroupCard {
        DesignCardBody {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Debian", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onOpenOfficialWebsite) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, "打开 Debian 官方网站", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                when (page) {
                    RuntimeDesignPage.Install -> "尚未安装"
                    RuntimeDesignPage.Download -> "正在下载基础环境"
                    else -> "下载中断"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (page == RuntimeDesignPage.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when (page) {
                RuntimeDesignPage.Install -> {
                    DesignValueRow("下载大小", "96 MB")
                    DesignValueRow("所需可用空间", "600 MB")
                    Spacer(Modifier.height(8.dp))
                    Text("首次准备需要下载基础环境，安装后可持续使用。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("安装并启用") }
                }
                RuntimeDesignPage.Download -> {
                    DesignValueRow("62 / 96 MB", "65%")
                    LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.fillMaxWidth())
                    Text("下载完成后将校验文件并解压安装。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("取消安装") }
                }
                else -> {
                    Text("网络连接已断开。已下载的文件会保留，恢复连接后可继续。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DesignValueRow("已下载", "62 / 96 MB")
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("继续下载") }
                }
            }
        }
    }
}

@Composable
private fun RuntimeVariablesFixture() {
    PreferenceSectionTitle("本地环境")
    Text("保存后对新启动的进程生效。", Modifier.padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DesignVariableFixture("PYTHONUNBUFFERED", "1", sensitive = false)
    Spacer(Modifier.height(12.dp))
    DesignVariableFixture("API_TOKEN", "preview-value", sensitive = true)
    TextButton(onClick = {}, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Text("添加变量", Modifier.padding(start = 6.dp))
    }
    Button(onClick = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("保存")
    }
}

@Composable
private fun DesignVariableFixture(name: String, value: String, sensitive: Boolean) {
    PreferenceGroupCard {
        DesignCardBody {
            OutlinedTextField(value = name, onValueChange = {}, label = { Text("变量名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = value, onValueChange = {}, label = { Text("变量值") }, singleLine = true,
                visualTransformation = if (sensitive) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth())
            IconButton(onClick = {}, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.DeleteOutline, "删除变量", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuntimeDirectoryFixture() {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("/", Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = {}) { Icon(Icons.Default.Refresh, "刷新目录") }
    }
    PreferenceGroupCard {
        Column(Modifier.padding(vertical = 12.dp)) {
            DesignTreeRow("bin", 0, directory = true)
            DesignTreeRow("etc", 0, directory = true)
            DesignTreeRow("home", 0, directory = true, expanded = true)
            DesignTreeRow("workspace", 1, directory = true, expanded = true)
            DesignTreeRow("exchange-report", 2, directory = true, expanded = true)
            DesignTreeRow("main.py", 3)
            DesignTreeRow("requirements.txt", 3)
            DesignTreeRow("node-demo", 2, directory = true)
            DesignTreeRow("tmp", 0, directory = true)
            DesignTreeRow("usr", 0, directory = true)
            DesignTreeRow("var", 0, directory = true)
        }
    }
}

@Composable
private fun DesignTreeRow(name: String, depth: Int, directory: Boolean = false, expanded: Boolean = false) {
    Surface(onClick = {}, enabled = directory, color = MaterialTheme.colorScheme.surface.copy(alpha = 0f)) {
        Row(Modifier.fillMaxWidth().padding(start = (16 + depth * 20).dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (directory) {
                Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, if (expanded) "收起目录" else "展开目录",
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RuntimeStopFixture() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("关闭本地环境？", style = MaterialTheme.typography.headlineSmall)
                Text("关闭后将停止正在执行的操作，环境文件和已安装的依赖会保留。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {}) { Text("取消") }
                    TextButton(onClick = {}) { Text("停止并关闭") }
                }
            }
        }
    }
}

@Composable
private fun DesignCardBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun DesignValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DesignChevron() {
    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
