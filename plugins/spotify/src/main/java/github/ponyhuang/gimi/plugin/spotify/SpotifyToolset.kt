package github.ponyhuang.gimi.plugin.spotify

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part

/** 在请求期暴露 Spotify 工具，并向模型补充跨工具协作规则。 */
internal class SpotifyToolset(
    private val tools: () -> List<BaseTool>,
) : Toolset {

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = tools()

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest = llmRequest.appendInstructions(
        Content(parts = listOf(Part(text = SPOTIFY_INSTRUCTIONS))),
    )

    override fun close() = Unit
}

private val SPOTIFY_INSTRUCTIONS: String = """
    <spotify>
    - Spotify tools operate on the current user's account. If authorization is missing, use `spotify_auth_status`
      to diagnose it and direct the user to authorize the plugin or use `spotify_login`; never invent account data.
    - Use `spotify_search` only for a concrete track, album, or artist. For the user's popular music or preferences,
      use `spotify_get_top_tracks` or `spotify_get_top_artists`; these are personal statistics, not global charts.
    - Discover accessible playlists with `spotify_get_my_playlists`, then reuse IDs returned by that tool for playlist
      reads or edits. Do not substitute public chart playlist IDs that may be inaccessible to the current account.
    - Reuse exact IDs or Spotify URIs returned by tools when fetching details, starting playback, queuing tracks, or
      editing playlists. Do not fabricate IDs, URIs, devices, or successful results.
    - Playback controls require Spotify Premium and an available Spotify Connect device. Use `spotify_get_devices`
      when the target is ambiguous or playback reports that no device is available.
    - Treat create, update, reorder, add, remove, playback, queue, and volume calls as mutations. Report success only
      after the corresponding tool succeeds, and surface its error or prerequisite when it fails.
    </spotify>
""".trimIndent()
