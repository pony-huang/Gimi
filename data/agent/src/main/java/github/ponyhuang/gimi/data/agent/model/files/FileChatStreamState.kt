/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package github.ponyhuang.gimi.data.agent.model.files

import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role

/** ADK 1.2.0 stream accumulation, retained for the file-aware SDK adapter. */
internal class ChatCompletionsStreamState {
  private var modelVersion: String? = null
  private val toolCalls = sortedMapOf<Int, ToolCallAccumulator>()

  fun onChunk(chunk: ChatCompletionResponse): List<LlmResponse> {
    chunk.error?.let {
      throw IllegalStateException(
        "Chat Completions stream error: ${it.type ?: it.code ?: "error"}"
      )
    }
    chunk.model?.let { modelVersion = it }
    val out = mutableListOf<LlmResponse>()
    val choice = chunk.choices.firstOrNull()
    val delta = choice?.delta
    delta?.contentText()?.takeIf { it.isNotEmpty() }?.let { out += textPartial(it) }
    delta?.toolCalls?.forEach { accumulate(it) }
    // Some servers send an empty `finish_reason` on every chunk before the real one.
    choice
      ?.finishReason
      ?.takeIf { it.isNotEmpty() }
      ?.let { reason ->
        val dropped = flushToolCalls(out)
        out += finishPartial(reason.toFinishReason(droppedMalformedCall = dropped), reason)
      }
    chunk.usage?.let {
      out +=
        LlmResponse(
          usageMetadata = it.toUsageMetadata(),
          modelVersion = modelVersion,
          partial = true,
        )
    }
    return out
  }

  private fun accumulate(toolCall: ChatToolCall) {
    // Some servers repeat an empty id or name on later chunks; neither may erase the first.
    val id = toolCall.id?.takeIf { it.isNotEmpty() }
    // Without an index, a delta with a new id starts a call and any other continues the last one.
    val startsCall = id != null && id != toolCalls.values.lastOrNull()?.id
    val index =
      toolCall.index ?: if (startsCall) toolCalls.size else (toolCalls.size - 1).coerceAtLeast(0)
    val accumulator = toolCalls.getOrPut(index) { ToolCallAccumulator() }
    id?.let { accumulator.id = it }
    toolCall.function?.name?.takeIf { it.isNotEmpty() }?.let { accumulator.name = it }
    toolCall.function?.arguments?.let { accumulator.arguments.append(it) }
    toolCall.extraContent.thoughtSignature()?.let { accumulator.thoughtSignature = it }
  }

  /**
   * Flushes tool calls left when the stream ends without a `finish_reason`, reporting a dropped
   * malformed call as MALFORMED_FUNCTION_CALL.
   */
  fun finish(): List<LlmResponse> {
    val out = mutableListOf<LlmResponse>()
    if (flushToolCalls(out)) out += finishPartial(FinishReason.MALFORMED_FUNCTION_CALL, null)
    return out
  }

  /**
   * Adds each buffered tool call to [out] as a partial and clears the buffer. A call with no name
   * or with malformed or truncated arguments is dropped rather than run; returns whether one was.
   */
  private fun flushToolCalls(out: MutableList<LlmResponse>): Boolean {
    var dropped = false
    for (accumulator in toolCalls.values) {
      val part =
        functionCallPart(
          accumulator.name,
          accumulator.arguments.toString(),
          accumulator.id,
          accumulator.thoughtSignature,
        )
      if (part == null) {
        dropped = true
        continue
      }
      out +=
        LlmResponse(
          content = Content(role = Role.MODEL, parts = listOf(part)),
          modelVersion = modelVersion,
          partial = true,
        )
    }
    toolCalls.clear()
    return dropped
  }

  private fun finishPartial(finishReason: FinishReason?, reason: String?): LlmResponse =
    LlmResponse(
      finishReason = finishReason,
      errorMessage = finishErrorMessage(finishReason, reason),
      modelVersion = modelVersion,
      partial = true,
    )

  private fun textPartial(text: String): LlmResponse =
    LlmResponse(
      content = Content(role = Role.MODEL, parts = listOf(Part(text = text))),
      modelVersion = modelVersion,
      partial = true,
    )

  private class ToolCallAccumulator {
    var id: String? = null
    var name: String? = null
    val arguments = StringBuilder()
    var thoughtSignature: ByteArray? = null
  }
}
