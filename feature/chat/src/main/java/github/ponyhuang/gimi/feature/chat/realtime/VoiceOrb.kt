package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.ui.theme.BrandBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 粒子数量：雾状微尘的密度下限，兼顾中端机每帧绘制成本（对照参考视频校准）。 */
private const val ParticleCount = 2400

/** 每颗粒子的静态参数步长：x/y/z、半径抖动、差速档位、尺寸、亮度、闪烁频率、闪烁相位、是否带品牌蓝。 */
private const val ParticleStride = 10

/** 预览与静音/失败态无帧动画时的固定相位，保证静态渲染仍是完整球形。 */
private const val PreviewYaw = 1.1f
private const val PreviewSwirl = 2.4f

/**
 * 粒子场静态参数（一次性生成、逐帧复用，无逐帧分配）：
 * 斐波那契球面均匀采样 + 半径抖动形成边缘更致密的球壳投影；
 * 差速档位 k 按球壳分级（外圈 0、中层 1、内圈 2），漩涡相位为整数倍周期，循环复位不跳变。
 * 固定种子保证 Preview 与真机形态一致。
 */
private class VoiceParticleField(count: Int) {
    val base = FloatArray(count * ParticleStride)
    val prevX = FloatArray(count) { Float.NaN }
    val prevY = FloatArray(count) { Float.NaN }

    init {
        val rng = Random(1013)
        val golden = PI * (3.0 - sqrt(5.0))
        for (i in 0 until count) {
            val o = i * ParticleStride
            val unitY = 1f - 2f * (i + .5f) / count
            val unitR = sqrt(max(0f, 1f - unitY * unitY))
            val phi = (i * golden).toFloat()
            base[o] = cos(phi) * unitR
            base[o + 1] = unitY
            base[o + 2] = sin(phi) * unitR
            // 半径抖动偏外侧：pow<1 抬高分布，形成视频里边缘致密、中心稀疏的观感
            val jit = .90f + rng.nextFloat().pow(.55f) * .15f
            base[o + 3] = jit
            base[o + 4] = when {
                jit < .93f -> 2f
                jit < .975f -> 1f
                else -> 0f
            }
            // 细雾为主，少量大亮星；亮度与闪烁相位彼此独立
            base[o + 5] = .5f + rng.nextFloat().pow(3f) * 1.5f
            base[o + 6] = .45f + rng.nextFloat() * .55f
            base[o + 7] = .5f + rng.nextFloat() * 1.6f
            base[o + 8] = rng.nextFloat() * 6.283f
            base[o + 9] = if (rng.nextFloat() < .09f) 1f else 0f
        }
    }
}

/**
 * 实时语音粒子球：微尘球体浮在主题画布上，保留参考视频的球体形态与运动语言。
 *
 * 粒子墨色跟随场景（浅色场景用深墨、深色场景用浅墨），播报时少量粒子混向品牌蓝，
 * 让球体成为这一屏唯一的彩色焦点；静音与失败降为暗尘。
 * 状态文字（statusText）居中球心，切换时交叉淡入淡出；球体本身纯装饰，语义由文字承载。
 *
 * @param ink 粒子与状态文字的墨色，由场景传入以便跟随主题明暗。
 */
@Composable
internal fun VoiceOrb(
    state: RealtimeVoiceState,
    switching: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
) {
    val quiet = state.muted || state.phase == RealtimeVoicePhase.Failed
    val speaking = state.phase == RealtimeVoicePhase.Speaking
    // 思考组含模式切换与识别中；Idle/Closed（待启动、连接中）归入缓动态而非漩涡。
    val thinking = switching || state.phase == RealtimeVoicePhase.Thinking || state.phase == RealtimeVoicePhase.Transcribing
    val listening = state.listening && !quiet && !speaking && !thinking
    val calm = !quiet && !speaking && !thinking && !listening
    val rawLevel = when {
        quiet || thinking || calm -> 0f
        speaking -> state.outputLevel.coerceIn(0f, 1f)
        else -> state.inputLevel.coerceIn(0f, 1f)
    }
    val level by animateFloatAsState(
        // 略提高轻声区间的视觉响应，零输入仍为零；只放大展示，不改录放音增益。
        targetValue = (rawLevel.pow(.58f) * 1.22f).coerceAtMost(1f),
        animationSpec = tween(80, easing = LinearEasing), label = "voiceLevel",
    )
    val inputContraction by animateFloatAsState(
        targetValue = if (listening) (rawLevel.pow(.58f) * 1.22f).coerceAtMost(1f) else 0f,
        animationSpec = tween(120, easing = LinearEasing), label = "voiceInputContraction",
    )
    val expansion by animateFloatAsState(
        targetValue = if (speaking) (rawLevel.pow(.58f) * 1.22f).coerceAtMost(1f) else 0f,
        animationSpec = tween(80, easing = LinearEasing), label = "voiceExpansion",
    )
    val dim by animateFloatAsState(
        targetValue = when {
            state.phase == RealtimeVoicePhase.Failed -> .28f
            state.muted -> .42f
            thinking -> .78f
            calm -> .8f
            else -> 1f
        },
        animationSpec = tween(500), label = "voiceDim",
    )
    val trail by animateFloatAsState(
        targetValue = if (speaking) 1f else if (thinking) .35f else 0f,
        animationSpec = tween(600), label = "voiceTrail",
    )
    val contract by animateFloatAsState(
        targetValue = if (thinking) .94f else 1f,
        animationSpec = tween(600), label = "voiceContract",
    )
    val tintStrength by animateFloatAsState(
        targetValue = if (speaking) .75f else 0f,
        animationSpec = tween(600), label = "voiceTint",
    )
    val preview = LocalInspectionMode.current
    val transition = if (!quiet && !preview) rememberInfiniteTransition(label = "voiceParticles") else null
    // Compose 动画运行时跟随系统动画时长缩放，关闭动画时不运行手写帧循环；
    // 双循环周期不同但粒子角度为整数倍相位，循环复位时投影连续不跳变。
    val yaw = transition?.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(48000, easing = LinearEasing)), label = "voiceYaw",
    )
        ?: remember { mutableFloatStateOf(PreviewYaw) }
    val swirlPhase = transition?.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "voiceSwirl",
    )
        ?: remember { mutableFloatStateOf(PreviewSwirl) }
    val particles = remember { VoiceParticleField(ParticleCount) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawParticleOrb(
                particles, yaw.value, swirlPhase.value, level, inputContraction, expansion,
                dim, trail, contract, tintStrength, ink,
            )
        }
        Crossfade(targetState = statusText, animationSpec = tween(200), label = "voiceStatus") { text ->
            Text(
                text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                color = ink.copy(alpha = .97f),
            )
        }
    }
}

/** 逐帧粒子绘制：旋转投影、深度明暗、边缘柔化、差速拖尾与品牌蓝混色，全部在 draw 阶段完成。 */
private fun DrawScope.drawParticleOrb(
    field: VoiceParticleField,
    yaw: Float,
    swirlPhase: Float,
    level: Float,
    inputContraction: Float,
    expansion: Float,
    dim: Float,
    trail: Float,
    contract: Float,
    tintStrength: Float,
    ink: Color,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    // 用户说话时整体等比例内收，粒子球与柔光共用半径，始终保持球形和固定球心。
    // 输入与输出分别平滑归零，让停顿、打断和播报切换时尺寸连续恢复。
    val breathing = 1f + .018f * sin(swirlPhase * 3f)
    val voiceScale = 1f + expansion * .05f - inputContraction * .22f
    val orbR = size.minDimension * .47f * contract * breathing * voiceScale
    // 球区柔光：云团整体与背景拉开一档明度（浅色场景是淡墨晕，深色场景是浅墨光晕）
    drawCircle(
        Brush.radialGradient(
            0f to ink.copy(alpha = .10f * dim),
            1f to ink.copy(alpha = 0f),
            center = center, radius = orbR * 1.3f,
        ),
        radius = orbR * 1.3f,
        center = center,
    )
    val tilt = .30f + .06f * sin(swirlPhase * 3f)
    val ct = cos(tilt)
    val st = sin(tilt)
    // 播报时少量粒子混向品牌蓝：这是全屏唯一的彩色层，其余一律保持中性墨色。
    val tinted = lerp(ink, BrandBlue, tintStrength)
    // 闪烁相位借用 48s 自转周期折算时间；复位只是重掷一次噪声，几何投影仍连续。
    val time = yaw * 48f
    val levelAlpha = 1f + level * .3f
    val levelSize = 1f + level * .18f
    val base = field.base
    for (i in 0 until ParticleCount) {
        val o = i * ParticleStride
        val jit = base[o + 3]
        val ang = yaw + base[o + 4] * swirlPhase
        val cs = cos(ang)
        val sn = sin(ang)
        val bx = base[o]
        val by = base[o + 1]
        val bz = base[o + 2]
        val x = bx * cs - bz * sn
        val z = bx * sn + bz * cs
        val y2 = by * ct - z * st
        val z2 = by * st + z * ct
        val radius = orbR * jit
        val px = center.x + x * radius
        val py = center.y + y2 * radius
        val d01 = (z2 + 1f) * .5f
        val shimmer = .55f + .45f * sin(time * base[o + 7] + base[o + 8])
        var alpha = base[o + 6] * (.26f + .74f * d01 * sqrt(d01)) * shimmer * dim * levelAlpha
        // 边缘柔化：出球壳后迅速淡出，避免硬轮廓
        val rOut = sqrt(x * x + y2 * y2) * jit
        if (rOut > .97f) alpha *= ((1.03f - rOut) / .06f).coerceIn(0f, 1f)
        val prevPx = field.prevX[i]
        val prevPy = field.prevY[i]
        field.prevX[i] = px
        field.prevY[i] = py
        if (alpha <= .004f) continue
        val pSize = base[o + 5] * (.70f + .50f * d01) * levelSize
        // 差速拖尾：仅较亮粒子，长度随状态强度伸缩；首帧无历史位置不画
        if (trail > .02f && base[o + 6] > .62f && prevPx == prevPx) {
            val dx = px - prevPx
            val dy = py - prevPy
            if (dx * dx + dy * dy > 2f) {
                val ext = 1f + 3f * trail
                drawLine(
                    ink,
                    start = Offset(px - dx * ext, py - dy * ext),
                    end = Offset(px, py),
                    strokeWidth = pSize * 1.8f,
                    cap = StrokeCap.Round,
                    alpha = (alpha * .5f * trail).coerceAtMost(.42f),
                )
            }
        }
        drawCircle(if (base[o + 9] > 0f) tinted else ink, pSize, Offset(px, py), alpha.coerceAtMost(1f))
    }
}
